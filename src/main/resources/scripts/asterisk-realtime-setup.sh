#!/usr/bin/env bash
# =============================================================================
#  asterisk-realtime-setup.sh
#  Configure Asterisk PJSIP Realtime (endpoints, auths, aors, contacts,
#  transports, ...) on MySQL/MariaDB through ODBC — fully automated.
#
#  What it does
#    1. Installs MariaDB server/client, unixODBC and the MariaDB ODBC driver
#    2. Creates the database + DB user and grants privileges
#    3. Creates the PJSIP realtime tables (Asterisk alembic if found,
#       otherwise the built-in schema below)
#    4. Writes /etc/odbcinst.ini, /etc/odbc.ini, res_odbc.conf,
#       extconfig.conf, sorcery.conf, pjsip.conf, modules.conf (preload)
#    5. Inserts the transport (UDP/TCP) for the SIP server IP
#    6. Optionally creates N sample extensions (endpoint + auth + aor)
#    7. Reloads Asterisk and verifies (odbc show / pjsip show endpoints)
#
#  Usage (interactive):
#    sudo ./asterisk-realtime-setup.sh
#
#  Usage (non-interactive):
#    sudo ./asterisk-realtime-setup.sh \
#         --sip-ip 10.10.1.102 --db-name asterisk \
#         --db-user asterisk --db-pass 'S3cret!' \
#         --ext-start 1001 --ext-count 10 --ext-pass 'Ext@1234'
#
#  Run with --help for all options.
# =============================================================================
set -Eeuo pipefail

# ----------------------------- defaults --------------------------------------
SIP_IP=""
DB_HOST="localhost"
DB_PORT="3306"
DB_NAME=""
DB_USER=""
DB_PASS="${AST_DB_PASS:-}"        # or env AST_DB_PASS (keeps it out of `ps`)
DB_ROOT_PASS=""            # empty = use unix_socket auth as root
DSN_NAME="asterisk"
ODBC_POOL_ID="asterisk"    # id used in res_odbc.conf / extconfig.conf
SIP_PORT="5060"
TRANSPORTS="udp,tcp"       # comma list: udp,tcp
TRANSPORT_MODE="realtime"  # realtime | static (static = transports in pjsip.conf)
LOCAL_NET="10.0.0.0/8"
EXT_START=""
EXT_COUNT="0"
EXT_PASS="${AST_EXT_PASS:-}"
EXT_CONTEXT="from-internal"
CODECS="ulaw,alaw,g722"
SKIP_INSTALL="no"
SKIP_DB_CREATE="no"        # yes = DB/user already exist (remote DB server)
ASSUME_YES="no"
AST_ETC="/etc/asterisk"
LOG_FILE="/var/log/asterisk-realtime-setup.log"

# ----------------------------- helpers ---------------------------------------
c_red=$'\e[31m'; c_grn=$'\e[32m'; c_ylw=$'\e[33m'; c_blu=$'\e[36m'; c_off=$'\e[0m'
[[ -t 1 ]] || { c_red=""; c_grn=""; c_ylw=""; c_blu=""; c_off=""; }   # no colours when run from Java/cron
log()  { echo "${c_blu}[INFO]${c_off} $*" | tee -a "$LOG_FILE"; }
ok()   { echo "${c_grn}[ OK ]${c_off} $*" | tee -a "$LOG_FILE"; }
warn() { echo "${c_ylw}[WARN]${c_off} $*" | tee -a "$LOG_FILE"; }
die()  { echo "${c_red}[FAIL]${c_off} $*" | tee -a "$LOG_FILE" >&2; exit 1; }
trap 'die "Error on line $LINENO: $BASH_COMMAND"' ERR

usage() {
cat <<EOF
Usage: $0 [options]

Required (prompted if missing):
  --sip-ip IP            SIP server IP address (bind / external address)
  --db-name NAME         Database name
  --db-user USER         Database user
  --db-pass PASS         Database password

Optional:
  --db-host HOST         Database host            (default: localhost)
  --db-port PORT         Database port            (default: 3306)
  --db-root-pass PASS    MySQL root password      (default: socket auth)
  --skip-db-create       DB + user already exist (e.g. remote DB server)
  --sip-port PORT        SIP port                 (default: 5060)
  --transports LIST      udp,tcp                  (default: udp,tcp)
  --transport-mode MODE  realtime|static          (default: realtime)
  --local-net CIDR       local_net for NAT        (default: 10.0.0.0/8)
  --ext-start N          First extension number   (e.g. 1001)
  --ext-count N          Number of extensions     (default: 0 = none)
  --ext-pass PASS        Password for extensions  (default: random each)
  --context NAME         Dialplan context         (default: from-internal)
  --codecs LIST          Allowed codecs           (default: ulaw,alaw,g722)
  --skip-install         Do not install packages
  -y, --yes              Do not ask for confirmation
  -h, --help             Show this help
EOF
}

# ----------------------------- arg parsing -----------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --sip-ip)         SIP_IP="$2"; shift 2 ;;
    --db-host)        DB_HOST="$2"; shift 2 ;;
    --db-port)        DB_PORT="$2"; shift 2 ;;
    --db-name)        DB_NAME="$2"; shift 2 ;;
    --db-user)        DB_USER="$2"; shift 2 ;;
    --db-pass)        DB_PASS="$2"; shift 2 ;;
    --db-root-pass)   DB_ROOT_PASS="$2"; shift 2 ;;
    --skip-db-create) SKIP_DB_CREATE="yes"; shift ;;
    --sip-port)       SIP_PORT="$2"; shift 2 ;;
    --transports)     TRANSPORTS="$2"; shift 2 ;;
    --transport-mode) TRANSPORT_MODE="$2"; shift 2 ;;
    --local-net)      LOCAL_NET="$2"; shift 2 ;;
    --ext-start)      EXT_START="$2"; shift 2 ;;
    --ext-count)      EXT_COUNT="$2"; shift 2 ;;
    --ext-pass)       EXT_PASS="$2"; shift 2 ;;
    --context)        EXT_CONTEXT="$2"; shift 2 ;;
    --codecs)         CODECS="$2"; shift 2 ;;
    --skip-install)   SKIP_INSTALL="yes"; shift ;;
    -y|--yes)         ASSUME_YES="yes"; shift ;;
    -h|--help)        usage; exit 0 ;;
    *) usage; die "Unknown option: $1" ;;
  esac
done

[[ $EUID -eq 0 ]] || die "Run as root (sudo)."
touch "$LOG_FILE" 2>/dev/null || LOG_FILE="/tmp/asterisk-realtime-setup.log"

# ----------------------------- prompts ---------------------------------------
ask() { # ask VAR "Prompt" [secret]
  local __var=$1 __prompt=$2 __secret=${3:-} __val=""
  [[ -n "${!__var}" ]] && return 0
  [[ -t 0 ]] || die "Missing value for '$__prompt' (no terminal to ask) — pass it as an option, see --help"
  while [[ -z "${!__var}" ]]; do
    if [[ -n "$__secret" ]]; then read -rsp "$__prompt: " __val; echo
    else read -rp "$__prompt: " __val; fi
    printf -v "$__var" '%s' "$__val"
  done
}
ask SIP_IP  "SIP server IP"
ask DB_NAME "Database name"
ask DB_USER "Database username"
ask DB_PASS "Database password" secret

# ----------------------------- validation ------------------------------------
valid_ip() {
  local ip=$1 IFS=. o
  [[ $ip =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || return 1
  for o in $ip; do (( o <= 255 )) || return 1; done
}
valid_ip "$SIP_IP"                     || die "Invalid SIP IP: $SIP_IP"
[[ $DB_NAME =~ ^[A-Za-z0-9_]+$ ]]      || die "DB name: letters/digits/_ only"
[[ $DB_USER =~ ^[A-Za-z0-9_]+$ ]]      || die "DB user: letters/digits/_ only"
[[ $TRANSPORT_MODE =~ ^(realtime|static)$ ]] || die "--transport-mode realtime|static"
[[ $EXT_COUNT =~ ^[0-9]+$ ]]           || die "--ext-count must be a number"
if (( EXT_COUNT > 0 )); then
  [[ $EXT_START =~ ^[0-9]+$ ]] || die "--ext-start is required with --ext-count"
fi

# SQL-escape single quotes and backslashes for literals
sql_esc() { local s=${1//\\/\\\\}; printf '%s' "${s//\'/\'\'}"; }
DB_PASS_SQL=$(sql_esc "$DB_PASS")

cat <<EOF

=========================== Summary ===========================
 SIP server IP   : $SIP_IP  (port $SIP_PORT, transports: $TRANSPORTS, $TRANSPORT_MODE)
 Database        : $DB_NAME @ $DB_HOST:$DB_PORT
 DB user         : $DB_USER
 ODBC DSN        : $DSN_NAME
 Extensions      : $( (( EXT_COUNT > 0 )) && echo "$EXT_COUNT starting at $EXT_START (context $EXT_CONTEXT)" || echo "none")
===============================================================
EOF
if [[ $ASSUME_YES != yes ]]; then
  read -rp "Proceed? [y/N] " a; [[ $a =~ ^[Yy]$ ]] || die "Aborted by user."
fi

# ----------------------------- OS detection ----------------------------------
. /etc/os-release
case "$ID ${ID_LIKE:-}" in
  *debian*|*ubuntu*) PKG=apt ;;
  *rhel*|*centos*|*fedora*|*rocky*|*almalinux*) PKG=dnf ;;
  *) PKG=unknown ;;
esac

# ----------------------------- 1. packages -----------------------------------
install_packages() {
  log "Installing packages ($PKG)..."
  if [[ $PKG == apt ]]; then
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -qq
    local pk=(unixodbc unixodbc-dev odbc-mariadb mariadb-client python3-alembic python3-pymysql)
    [[ $DB_HOST == localhost || $DB_HOST == 127.0.0.1 ]] && pk+=(mariadb-server)
    apt-get install -y -qq "${pk[@]}" >>"$LOG_FILE" 2>&1
  elif [[ $PKG == dnf ]]; then
    local pk=(unixODBC unixODBC-devel mariadb-connector-odbc mariadb python3-pip)
    [[ $DB_HOST == localhost || $DB_HOST == 127.0.0.1 ]] && pk+=(mariadb-server)
    dnf install -y -q "${pk[@]}" >>"$LOG_FILE" 2>&1
  else
    die "Unsupported OS ($ID). Install unixODBC + MariaDB ODBC manually and use --skip-install."
  fi
  if [[ $DB_HOST == localhost || $DB_HOST == 127.0.0.1 ]]; then
    systemctl enable --now mariadb >>"$LOG_FILE" 2>&1 \
      || service mariadb start >>"$LOG_FILE" 2>&1 \
      || warn "Could not start mariadb via systemctl/service — start it manually."
  fi
  ok "Packages installed"
}
[[ $SKIP_INSTALL == yes ]] || install_packages

command -v mysql >/dev/null || die "mysql client not found"
command -v isql  >/dev/null || die "isql (unixODBC) not found"

# ----------------------------- 2. database -----------------------------------
ROOT_ARGS=(-u root)
[[ -n $DB_ROOT_PASS ]] && ROOT_ARGS+=(-p"$DB_ROOT_PASS")
[[ $DB_HOST != localhost ]] && ROOT_ARGS+=(-h "$DB_HOST" -P "$DB_PORT")

if [[ $SKIP_DB_CREATE != yes ]]; then
  log "Creating database '$DB_NAME' and user '$DB_USER'..."
  mysql "${ROOT_ARGS[@]}" <<SQL
CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASS_SQL';
CREATE USER IF NOT EXISTS '$DB_USER'@'%'         IDENTIFIED BY '$DB_PASS_SQL';
ALTER USER '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASS_SQL';
ALTER USER '$DB_USER'@'%'         IDENTIFIED BY '$DB_PASS_SQL';
GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USER'@'localhost';
GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USER'@'%';
FLUSH PRIVILEGES;
SQL
  ok "Database and user ready"
fi

# Run SQL as the app user (password via env, not on the command line)
dbq() {
  local args=(-u "$DB_USER")
  [[ $DB_HOST != localhost ]] && args+=(-h "$DB_HOST" -P "$DB_PORT")
  MYSQL_PWD="$DB_PASS" mysql "${args[@]}" "$@" "$DB_NAME"
}
echo "SELECT 1;" | dbq >/dev/null || die "Cannot connect to DB as $DB_USER"
ok "DB login as '$DB_USER' works"

# ----------------------------- 3. schema -------------------------------------
create_schema_alembic() {
  local dir
  dir=$(ls -d /usr/src/asterisk*/contrib/ast-db-manage 2>/dev/null | sort -V | tail -1) || true
  [[ -n $dir && -d $dir ]] || return 1
  log "Using Asterisk alembic schema from $dir"
  if ! python3 -c 'import alembic, pymysql' 2>/dev/null; then
    pip3 install -q alembic pymysql >>"$LOG_FILE" 2>&1 \
      || pip3 install -q --break-system-packages alembic pymysql >>"$LOG_FILE" 2>&1 \
      || { warn "alembic/pymysql not installable — falling back to built-in schema"; return 1; }
  fi
  local tmp enc_pass rc=0
  tmp=$(mktemp -d)
  cp -a "$dir"/. "$tmp"/
  enc_pass=$(python3 -c 'import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1],safe=""))' "$DB_PASS")
  local host=$DB_HOST; [[ $host == localhost ]] && host=127.0.0.1
  sed -i "s#^sqlalchemy.url.*#sqlalchemy.url = mysql+pymysql://${DB_USER}:${enc_pass//%/%%}@${host}:${DB_PORT}/${DB_NAME}#" "$tmp/config.ini.sample"
  (cd "$tmp" && python3 -m alembic -c config.ini.sample upgrade head) >"$tmp/alembic.out" 2>&1 || rc=$?
  cat "$tmp/alembic.out" >>"$LOG_FILE"
  if (( rc != 0 )); then
    warn "alembic failed (falling back to built-in schema). Last lines:"
    { grep -E 'Error|Exception|No module|not found' "$tmp/alembic.out" | grep -v '^ ' | tail -2 \
        || tail -2 "$tmp/alembic.out"; } | cut -c1-220 | sed 's/^/        /'
  fi
  rm -rf "$tmp"
  return $rc
}

builtin_sql() {
  cat <<'SQL'
CREATE TABLE IF NOT EXISTS ps_transports (
  id VARCHAR(40) NOT NULL PRIMARY KEY,
  async_operations INT, bind VARCHAR(40), ca_list_file VARCHAR(200),
  cert_file VARCHAR(200), cipher VARCHAR(200), domain VARCHAR(40),
  external_media_address VARCHAR(40), external_signaling_address VARCHAR(40),
  external_signaling_port INT, method VARCHAR(40), local_net VARCHAR(40),
  password VARCHAR(40), priv_key_file VARCHAR(200),
  protocol ENUM('udp','tcp','tls','ws','wss','flow'),
  require_client_cert VARCHAR(40), verify_client VARCHAR(40), verify_server VARCHAR(40),
  tos VARCHAR(10), cos INT, allow_reload VARCHAR(5), symmetric_transport VARCHAR(5),
  allow_wildcard_certs VARCHAR(5)
);
CREATE TABLE IF NOT EXISTS ps_endpoints (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  transport VARCHAR(40), aors VARCHAR(2048), auth VARCHAR(255),
  context VARCHAR(40), disallow VARCHAR(200), allow VARCHAR(200),
  direct_media ENUM('yes','no'), connected_line_method VARCHAR(40),
  direct_media_method VARCHAR(40), direct_media_glare_mitigation VARCHAR(40),
  disable_direct_media_on_nat ENUM('yes','no'), dtmf_mode VARCHAR(40),
  external_media_address VARCHAR(40), force_rport ENUM('yes','no'),
  ice_support ENUM('yes','no'), identify_by VARCHAR(80), mailboxes VARCHAR(40),
  moh_suggest VARCHAR(40), outbound_auth VARCHAR(255), outbound_proxy VARCHAR(255),
  rewrite_contact ENUM('yes','no'), rtp_ipv6 ENUM('yes','no'), rtp_symmetric ENUM('yes','no'),
  send_diversion ENUM('yes','no'), send_pai ENUM('yes','no'), send_rpid ENUM('yes','no'),
  timers_min_se INT, timers VARCHAR(40), timers_sess_expires INT,
  callerid VARCHAR(40), callerid_privacy VARCHAR(40), callerid_tag VARCHAR(40),
  `100rel` VARCHAR(40), aggregate_mwi ENUM('yes','no'), trust_id_inbound ENUM('yes','no'),
  trust_id_outbound ENUM('yes','no'), use_ptime ENUM('yes','no'), use_avpf ENUM('yes','no'),
  media_encryption VARCHAR(40), inband_progress ENUM('yes','no'), call_group VARCHAR(40),
  pickup_group VARCHAR(40), named_call_group VARCHAR(40), named_pickup_group VARCHAR(40),
  device_state_busy_at INT, fax_detect ENUM('yes','no'), t38_udptl ENUM('yes','no'),
  t38_udptl_ec VARCHAR(40), t38_udptl_maxdatagram INT, t38_udptl_nat ENUM('yes','no'),
  t38_udptl_ipv6 ENUM('yes','no'), tone_zone VARCHAR(40), language VARCHAR(40),
  one_touch_recording ENUM('yes','no'), record_on_feature VARCHAR(40),
  record_off_feature VARCHAR(40), rtp_engine VARCHAR(40), allow_transfer ENUM('yes','no'),
  allow_subscribe ENUM('yes','no'), sdp_owner VARCHAR(40), sdp_session VARCHAR(40),
  tos_audio VARCHAR(10), tos_video VARCHAR(10), sub_min_expiry INT, from_domain VARCHAR(40),
  from_user VARCHAR(40), mwi_from_user VARCHAR(40), dtls_verify VARCHAR(40),
  dtls_rekey VARCHAR(40), dtls_cert_file VARCHAR(200), dtls_private_key VARCHAR(200),
  dtls_cipher VARCHAR(200), dtls_ca_file VARCHAR(200), dtls_ca_path VARCHAR(200),
  dtls_setup VARCHAR(40), dtls_fingerprint VARCHAR(40), srtp_tag_32 ENUM('yes','no'),
  media_address VARCHAR(40), redirect_method VARCHAR(40), set_var TEXT,
  cos_audio INT, cos_video INT, message_context VARCHAR(40),
  force_avp ENUM('yes','no'), media_use_received_transport ENUM('yes','no'),
  accountcode VARCHAR(80), user_eq_phone ENUM('yes','no'),
  moh_passthrough ENUM('yes','no'), media_encryption_optimistic ENUM('yes','no'),
  rpid_immediate ENUM('yes','no'), g726_non_standard ENUM('yes','no'),
  rtp_keepalive INT, rtp_timeout INT, rtp_timeout_hold INT,
  bind_rtp_to_media_address ENUM('yes','no'), voicemail_extension VARCHAR(40),
  mwi_subscribe_replaces_unsolicited ENUM('yes','no'), deny VARCHAR(95), permit VARCHAR(95),
  acl VARCHAR(40), contact_deny VARCHAR(95), contact_permit VARCHAR(95), contact_acl VARCHAR(40),
  subscribe_context VARCHAR(40), fax_detect_timeout INT, contact_user VARCHAR(80),
  preferred_codec_only ENUM('yes','no'), asymmetric_rtp_codec ENUM('yes','no'),
  rtcp_mux ENUM('yes','no'), allow_overlap ENUM('yes','no'), refer_blind_progress ENUM('yes','no'),
  notify_early_inuse_ringing ENUM('yes','no'), max_audio_streams INT, max_video_streams INT,
  webrtc ENUM('yes','no'), dtls_fingerprint_type VARCHAR(40), incoming_mwi_mailbox VARCHAR(40),
  bundle ENUM('yes','no'), dtls_auto_generate_cert ENUM('yes','no'),
  follow_early_media_fork ENUM('yes','no'), accept_multiple_sdp_answers ENUM('yes','no'),
  suppress_q850_reason_headers ENUM('yes','no'), trust_connected_line ENUM('yes','no'),
  send_connected_line ENUM('yes','no'), ignore_183_without_sdp ENUM('yes','no'),
  codec_prefs_incoming_offer VARCHAR(128), codec_prefs_outgoing_offer VARCHAR(128),
  codec_prefs_incoming_answer VARCHAR(128), codec_prefs_outgoing_answer VARCHAR(128),
  stir_shaken ENUM('yes','no'), send_history_info ENUM('yes','no'),
  allow_unauthenticated_options ENUM('yes','no'), t38_bind_udptl_to_media_address ENUM('yes','no'),
  geoloc_incoming_call_profile VARCHAR(80), geoloc_outgoing_call_profile VARCHAR(80),
  incoming_call_offer_pref VARCHAR(80), outgoing_call_offer_pref VARCHAR(80),
  stir_shaken_profile VARCHAR(80), security_negotiation VARCHAR(40),
  security_mechanisms VARCHAR(512), send_aoc ENUM('yes','no'), overlap_context VARCHAR(80),
  tenantid VARCHAR(80), suppress_moh_on_sendonly ENUM('yes','no')
);
CREATE TABLE IF NOT EXISTS ps_auths (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  auth_type VARCHAR(40), nonce_lifetime INT, md5_cred VARCHAR(40),
  password VARCHAR(80), realm VARCHAR(255), username VARCHAR(255),
  refresh_token VARCHAR(255), oauth_clientid VARCHAR(255), oauth_secret VARCHAR(255),
  password_digest VARCHAR(1024), supported_algorithms_uac VARCHAR(1024),
  supported_algorithms_uas VARCHAR(1024)
);
CREATE TABLE IF NOT EXISTS ps_aors (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  contact VARCHAR(255), default_expiration INT, mailboxes VARCHAR(80),
  max_contacts INT, minimum_expiration INT, remove_existing ENUM('yes','no'),
  qualify_frequency INT, authenticate_qualify ENUM('yes','no'),
  maximum_expiration INT, outbound_proxy VARCHAR(255), support_path ENUM('yes','no'),
  qualify_timeout FLOAT, voicemail_extension VARCHAR(40),
  remove_unavailable ENUM('yes','no'), qualify_2xx_only ENUM('yes','no')
);
CREATE TABLE IF NOT EXISTS ps_contacts (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  uri VARCHAR(511), expiration_time BIGINT, qualify_frequency INT,
  outbound_proxy VARCHAR(255), path TEXT, user_agent VARCHAR(255),
  qualify_timeout FLOAT, reg_server VARCHAR(255), authenticate_qualify ENUM('yes','no'),
  via_addr VARCHAR(40), via_port INT, call_id VARCHAR(255), endpoint VARCHAR(255),
  prune_on_boot ENUM('yes','no'), qualify_2xx_only ENUM('yes','no'),
  UNIQUE KEY ps_contacts_uq (id, reg_server),
  KEY ps_contacts_qualifyfreq_exp (qualify_frequency, expiration_time)
);
CREATE TABLE IF NOT EXISTS ps_domain_aliases (
  id VARCHAR(255) NOT NULL PRIMARY KEY, domain VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS ps_endpoint_id_ips (
  id VARCHAR(255) NOT NULL PRIMARY KEY, endpoint VARCHAR(255), `match` VARCHAR(80),
  srv_lookups ENUM('yes','no'), match_header VARCHAR(255), match_request_uri VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS ps_registrations (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  auth_rejection_permanent ENUM('yes','no'), client_uri VARCHAR(255),
  contact_user VARCHAR(40), expiration INT, max_retries INT, outbound_auth VARCHAR(255),
  outbound_proxy VARCHAR(255), retry_interval INT, forbidden_retry_interval INT,
  server_uri VARCHAR(255), transport VARCHAR(40), support_path ENUM('yes','no'),
  fatal_retry_interval INT, line ENUM('yes','no'), endpoint VARCHAR(255),
  support_outbound ENUM('yes','no'), contact_header_params VARCHAR(255),
  max_random_initial_delay INT, security_negotiation VARCHAR(40),
  security_mechanisms VARCHAR(512), user_agent VARCHAR(255)
);
SQL
}

create_schema_builtin() {
  log "Creating PJSIP realtime tables (built-in schema)..."
  builtin_sql | dbq
}

# Existing tables (old manual setup / partial alembic run) may lack columns.
# Any ps_* table missing required columns is renamed to <table>_bak_<timestamp>,
# recreated with the full schema, and its rows are copied back.
sync_missing_columns() {
  log "Checking existing tables for missing columns..."
  builtin_sql | sed -E 's/CREATE TABLE IF NOT EXISTS (ps_[a-z_]+)/DROP TABLE IF EXISTS zz_tmpl_\1; CREATE TABLE zz_tmpl_\1/' | dbq
  local tables t ts cols
  ts=$(date +%Y%m%d%H%M%S)
  tables=$(dbq -N <<'SQL'
SELECT DISTINCT SUBSTRING(t.table_name, 9)
FROM information_schema.columns t
LEFT JOIN information_schema.columns r
  ON r.table_schema = t.table_schema
 AND r.table_name   = SUBSTRING(t.table_name, 9)
 AND r.column_name  = t.column_name
WHERE t.table_schema = DATABASE()
  AND t.table_name LIKE 'zz\_tmpl\_%'
  AND r.column_name IS NULL;
SQL
)
  if [[ -z $tables ]]; then
    ok "All tables have the required columns"
  fi
  for t in $tables; do
    cols=$(dbq -N -e "SELECT GROUP_CONCAT(CONCAT('\`', r.column_name, '\`') ORDER BY r.ordinal_position)
                      FROM information_schema.columns r
                      JOIN information_schema.columns t
                        ON t.table_schema = r.table_schema AND t.table_name = 'zz_tmpl_$t' AND t.column_name = r.column_name
                      WHERE r.table_schema = DATABASE() AND r.table_name = '$t'")
    dbq <<SQL
RENAME TABLE \`$t\` TO \`${t}_bak_$ts\`;
CREATE TABLE \`$t\` LIKE \`zz_tmpl_$t\`;
INSERT IGNORE INTO \`$t\` ($cols) SELECT $cols FROM \`${t}_bak_$ts\`;
SQL
    ok "Upgraded table '$t' to the full schema (old copy kept as ${t}_bak_$ts, rows copied)"
  done
  dbq -N -e "SELECT CONCAT('DROP TABLE \`', table_name, '\`;') FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'zz\\_tmpl\\_%'" | dbq
}

if create_schema_alembic; then
  ok "Schema created with Asterisk alembic"
else
  create_schema_builtin
  ok "Schema created (built-in)"
  sync_missing_columns
fi

# ----------------------------- 4. ODBC ---------------------------------------
log "Configuring unixODBC..."
ODBC_LIB=$(ls /usr/lib/x86_64-linux-gnu/odbc/libmaodbc.so /usr/lib/aarch64-linux-gnu/odbc/libmaodbc.so \
               /usr/lib64/libmaodbc.so /usr/lib/libmaodbc.so /usr/lib64/mariadb/libmaodbc.so 2>/dev/null | head -1 || true)
[[ -n $ODBC_LIB ]] || ODBC_LIB=$(find / -name 'libmaodbc.so' -type f 2>/dev/null | head -1 || true)
[[ -n $ODBC_LIB ]] || die "MariaDB ODBC driver (libmaodbc.so) not found"

backup() { [[ -f $1 ]] && cp -a "$1" "$1.bak.$(date +%Y%m%d%H%M%S)" || true; }

# ini_section_replace FILE SECTION CONTENT  — replace/append one [section]
ini_section_replace() {
  local file=$1 sect=$2 content=$3
  touch "$file"
  awk -v s="[$sect]" '
    $0==s {skip=1; next}
    /^\[/ && skip {skip=0}
    !skip' "$file" > "$file.tmp"
  printf '\n%s\n' "$content" >> "$file.tmp"
  mv "$file.tmp" "$file"
}

backup /etc/odbcinst.ini
ini_section_replace /etc/odbcinst.ini "MariaDB" "[MariaDB]
Description = MariaDB ODBC Connector
Driver      = $ODBC_LIB
Threading   = 0
UsageCount  = 1"

SOCKET_LINE=""
if [[ $DB_HOST == localhost ]]; then
  SOCK=$(mysql "${ROOT_ARGS[@]}" -Nse "SELECT @@socket" 2>/dev/null || echo /run/mysqld/mysqld.sock)
  SOCKET_LINE="Socket      = $SOCK"
fi
backup /etc/odbc.ini
ini_section_replace /etc/odbc.ini "$DSN_NAME" "[$DSN_NAME]
Description = Asterisk realtime
Driver      = MariaDB
Server      = $DB_HOST
Port        = $DB_PORT
Database    = $DB_NAME
User        = $DB_USER
Password    = $DB_PASS
$SOCKET_LINE
Option      = 3"
chmod 640 /etc/odbc.ini
getent group asterisk >/dev/null && chgrp asterisk /etc/odbc.ini || true

echo "SELECT 1" | isql -b "$DSN_NAME" "$DB_USER" "$DB_PASS" >/dev/null 2>&1 \
  || die "ODBC test failed: isql -v $DSN_NAME $DB_USER <pass>"
ok "ODBC DSN '$DSN_NAME' works (driver: $ODBC_LIB)"

# ----------------------------- 5. Asterisk config ----------------------------
mkdir -p "$AST_ETC"
log "Writing Asterisk configuration in $AST_ETC ..."
for f in res_odbc.conf extconfig.conf sorcery.conf pjsip.conf modules.conf; do backup "$AST_ETC/$f"; done

cat > "$AST_ETC/res_odbc.conf" <<EOF
; Generated by asterisk-realtime-setup.sh on $(date)
[$ODBC_POOL_ID]
enabled => yes
dsn => $DSN_NAME
username => $DB_USER
password => $DB_PASS
pre-connect => yes
max_connections => 20
sanitysql => select 1
EOF
chmod 640 "$AST_ETC/res_odbc.conf"

cat > "$AST_ETC/extconfig.conf" <<EOF
; Generated by asterisk-realtime-setup.sh on $(date)
[settings]
ps_endpoints       => odbc,$ODBC_POOL_ID
ps_auths           => odbc,$ODBC_POOL_ID
ps_aors            => odbc,$ODBC_POOL_ID
ps_contacts        => odbc,$ODBC_POOL_ID
ps_domain_aliases  => odbc,$ODBC_POOL_ID
ps_endpoint_id_ips => odbc,$ODBC_POOL_ID
ps_registrations   => odbc,$ODBC_POOL_ID
EOF
[[ $TRANSPORT_MODE == realtime ]] && echo "ps_transports      => odbc,$ODBC_POOL_ID" >> "$AST_ETC/extconfig.conf"

{
  echo "; Generated by asterisk-realtime-setup.sh on $(date)"
  echo "[res_pjsip]"
  echo "endpoint=realtime,ps_endpoints"
  echo "auth=realtime,ps_auths"
  echo "aor=realtime,ps_aors"
  echo "domain_alias=realtime,ps_domain_aliases"
  echo "contact=realtime,ps_contacts"
  [[ $TRANSPORT_MODE == realtime ]] && echo "transport=realtime,ps_transports"
  echo
  echo "[res_pjsip_endpoint_identifier_ip]"
  echo "identify=realtime,ps_endpoint_id_ips"
  echo
  echo "[res_pjsip_outbound_registration]"
  echo "registration=realtime,ps_registrations"
} > "$AST_ETC/sorcery.conf"

# pjsip.conf: global settings (+ static transports if chosen)
{
  echo "; Generated by asterisk-realtime-setup.sh on $(date)"
  echo "; Endpoints / AORs / Auths live in database '$DB_NAME'"
  echo "[global]"
  echo "type=global"
  echo "user_agent=Asterisk PBX"
  echo "endpoint_identifier_order=ip,username,anonymous"
  if [[ $TRANSPORT_MODE == static ]]; then
    IFS=, read -ra TP <<<"$TRANSPORTS"
    for p in "${TP[@]}"; do
      cat <<EOT

[transport-$p]
type=transport
protocol=$p
bind=$SIP_IP:$SIP_PORT
local_net=$LOCAL_NET
EOT
    done
  fi
} > "$AST_ETC/pjsip.conf"

# modules.conf: res_odbc + res_config_odbc must be preloaded before res_pjsip
if [[ -f $AST_ETC/modules.conf ]] && grep -q '^\[modules\]' "$AST_ETC/modules.conf"; then
  # remove any noload of the ODBC modules (Debian/Ubuntu ship noload res_config_odbc.so)
  sed -i -E 's/^\s*noload\s*=>?\s*(res_odbc|res_config_odbc)\.so/;&  ; disabled by realtime setup/' "$AST_ETC/modules.conf"
  # preload order matters: res_odbc first, then res_config_odbc (insert in reverse)
  for m in res_config_odbc.so res_odbc.so; do
    sed -i -E "/^\s*preload\s*=>?\s*${m//./\\.}/d" "$AST_ETC/modules.conf"
    sed -i "/^\[modules\]/a preload => $m" "$AST_ETC/modules.conf"
  done
  grep -qE '^\s*noload\s*=>?\s*chan_sip\.so' "$AST_ETC/modules.conf" \
    || sed -i "/^\[modules\]/a noload => chan_sip.so" "$AST_ETC/modules.conf"
else
  cat > "$AST_ETC/modules.conf" <<EOF
[modules]
autoload=yes
preload => res_odbc.so
preload => res_config_odbc.so
noload => chan_sip.so
EOF
fi
getent passwd asterisk >/dev/null && chown asterisk:asterisk "$AST_ETC"/{res_odbc,extconfig,sorcery,pjsip,modules}.conf || true
ok "Asterisk config files written (backups: *.bak.<timestamp>)"

# ----------------------------- 6. data ---------------------------------------
if [[ $TRANSPORT_MODE == realtime ]]; then
  log "Inserting transports for $SIP_IP:$SIP_PORT ..."
  IFS=, read -ra TP <<<"$TRANSPORTS"
  for p in "${TP[@]}"; do
    dbq <<SQL
INSERT INTO ps_transports (id, protocol, bind, local_net, allow_reload)
VALUES ('transport-$p', '$p', '$SIP_IP:$SIP_PORT', '$LOCAL_NET', 'no')
ON DUPLICATE KEY UPDATE protocol=VALUES(protocol), bind=VALUES(bind), local_net=VALUES(local_net);
SQL
  done
  ok "Transports: $TRANSPORTS"
fi
DEFAULT_TRANSPORT="transport-${TRANSPORTS%%,*}"

CRED_FILE="/root/asterisk-extensions-$(date +%Y%m%d%H%M%S).txt"
if (( EXT_COUNT > 0 )); then
  log "Creating $EXT_COUNT extensions from $EXT_START ..."
  : > "$CRED_FILE"; chmod 600 "$CRED_FILE"
  printf '%-10s %-20s %s\n' "EXT" "PASSWORD" "SERVER" >> "$CRED_FILE"
  for (( i=0; i<EXT_COUNT; i++ )); do
    ext=$((EXT_START + i))
    pw=$EXT_PASS
    [[ -n $pw ]] || pw=$(set +o pipefail; LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 14)
    pw_sql=$(sql_esc "$pw")
    dbq <<SQL
INSERT INTO ps_aors (id, max_contacts, remove_existing, qualify_frequency)
VALUES ('$ext', 1, 'yes', 60)
ON DUPLICATE KEY UPDATE max_contacts=VALUES(max_contacts);
INSERT INTO ps_auths (id, auth_type, username, password)
VALUES ('$ext', 'userpass', '$ext', '$pw_sql')
ON DUPLICATE KEY UPDATE password=VALUES(password);
INSERT INTO ps_endpoints (id, transport, aors, auth, context, disallow, allow,
  direct_media, rtp_symmetric, force_rport, rewrite_contact, dtmf_mode, callerid)
VALUES ('$ext', '$DEFAULT_TRANSPORT', '$ext', '$ext', '$EXT_CONTEXT', 'all', '$CODECS',
  'no', 'yes', 'yes', 'yes', 'rfc4733', 'Ext $ext <$ext>')
ON DUPLICATE KEY UPDATE transport=VALUES(transport), context=VALUES(context), allow=VALUES(allow);
SQL
    printf '%-10s %-20s %s\n' "$ext" "$pw" "$SIP_IP:$SIP_PORT" >> "$CRED_FILE"
  done
  ok "Extensions created — credentials saved to $CRED_FILE"
fi

# ----------------------------- 7. reload & verify ----------------------------
if command -v asterisk >/dev/null; then
  log "Restarting Asterisk (transports/preloads need a full restart)..."
  systemctl restart asterisk >>"$LOG_FILE" 2>&1 || service asterisk restart >>"$LOG_FILE" 2>&1 \
    || warn "Could not restart Asterisk automatically"
  sleep 5
  echo; log "odbc show:";            asterisk -rx "odbc show"              | tee -a "$LOG_FILE" || true
  echo; log "pjsip show transports:"; asterisk -rx "pjsip show transports" | tee -a "$LOG_FILE" || true
  echo; log "pjsip show endpoints:";  asterisk -rx "pjsip show endpoints"  | tee -a "$LOG_FILE" || true
else
  warn "Asterisk binary not found — config written; start Asterisk and run: asterisk -rx 'pjsip show endpoints'"
fi

echo
ok "Done. SIP server: $SIP_IP:$SIP_PORT | DB: $DB_NAME | Log: $LOG_FILE"
