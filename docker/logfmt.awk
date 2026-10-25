# Confess web — pretty nginx logs, same shape as the API's (app/core/logging.py):
#
#   ──────────── Sun 25 Oct 2026 ────────────
#   16:10:50 ──▶    web        GET    /                                        200      1ms  49.36.181.206  Mac · Chrome
#   16:10:51 ──▶    web        GET    /p/5fb0cc12...                           404      0ms  49.36.181.206  iPhone · Safari
#   16:26:04 ERROR  nginx      connect() failed (111: Connection refused) …  · GET /
#
# Input: access lines (tab-separated, log_format confess in nginx.conf) and nginx error lines,
# both written to a pipe. POSIX awk only (runs on BusyBox awk in the image). colors=1|0.

BEGIN {
    FS = "\t"
    E = sprintf("%c", 27)
    if (colors == "1") {
        RESET = E "[0m"; DIM = E "[2m"; BOLD = E "[1m"
        RED = E "[31m"; GREEN = E "[32m"; YELLOW = E "[33m"; CYAN = E "[36m"; COMP = E "[96m"; NGX = E "[95m"
    }
    split("Jan Feb Mar Apr May Jun Jul Aug Sep Oct Nov Dec", MON, " ")
    split("Sun Mon Tue Wed Thu Fri Sat", WD, " ")
    last_day = ""
}

function c(code, text) { return (code == "") ? text : code text RESET }

# Day of week (0 = Sunday), Sakamoto's method.
function weekday(y, m, d,    t) {
    split("0 3 2 5 0 3 5 1 4 6 2 4", t, " ")
    if (m < 3) y -= 1
    return (y + int(y / 4) - int(y / 100) + int(y / 400) + t[m] + d) % 7
}

function day_line(y, m, d,    key) {
    key = y "-" m "-" d
    if (key == last_day) return
    last_day = key
    printf "%s\n", c(DIM, "──────────── " WD[weekday(y + 0, m + 0, d + 0) + 1] " " d " " MON[m + 0] " " y " ────────────")
}

# Shorten long paths: uuids → first 8 chars + "...", then cap at 40 characters (ASCII only, so
# byte-counting awks still line the columns up).
function short(p,    out, i, n, parts) {
    n = split(p, parts, "/")
    out = ""
    for (i = 1; i <= n; i++) {
        if (parts[i] ~ /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/)
            parts[i] = substr(parts[i], 1, 8) "..."
        out = out (i > 1 ? "/" : "") parts[i]
    }
    if (length(out) > 40) out = substr(out, 1, 37) "..."
    return out
}

function status_color(s) {
    s = s + 0
    if (s < 300) return GREEN
    if (s < 400) return CYAN
    if (s < 500) return YELLOW
    return RED BOLD
}

function pad(s, w) { return sprintf("%-" w "s", s) }

# ── access line: time  status  method  uri  seconds  bytes  ip  device ──
NF >= 8 {
    iso = $1
    day_line(substr(iso, 1, 4), substr(iso, 6, 2), substr(iso, 9, 2))
    clock = substr(iso, 12, 8)
    ms = int($5 * 1000 + 0.5)
    dcol = (ms >= 1000) ? RED BOLD : (ms >= 300) ? YELLOW : DIM
    dur = (ms < 10000) ? sprintf("%5dms", ms) : sprintf("%5.1fs", ms / 1000)
    st = $2 + 0
    arrow = c((st >= 500) ? RED BOLD : DIM, "──▶") "  "     # "──▶" is 3 columns but 9 bytes: pad by hand
    printf "%s %s  %s %s %s %s %s  %s  %s\n", \
        c(DIM, clock), arrow, c(COMP, pad("web", 10)), c(BOLD, pad($3, 6)), pad(short($4), 40), \
        c(status_color($2), sprintf("%3s", $2)), c(dcol, dur), c(DIM, $7), c(DIM, $8)
    fflush()
    next
}

# ── nginx error line: 2026/10/25 16:26:04 [error] 21#21: *8 message, client: …, request: "GET / HTTP/1.1", … ──
/^[0-9][0-9][0-9][0-9]\/[0-9][0-9]\/[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9] \[/ {
    line = $0
    day_line(substr(line, 1, 4), substr(line, 6, 2), substr(line, 9, 2))
    clock = substr(line, 12, 8)
    rest = substr(line, 22)
    lvl = substr(rest, 1, index(rest, "]") - 1)
    msg = substr(rest, index(rest, "]") + 2)
    sub(/^[0-9]+#[0-9]+: /, "", msg)            # pid#tid
    sub(/^\*[0-9]+ /, "", msg)                  # connection id
    where = ""
    i = index(msg, ", request: \"")
    if (i > 0) {
        req = substr(msg, i + 12)
        req = substr(req, 1, index(req, "\"") - 1)
        split(req, rq, " ")
        where = "  " c(DIM, "· " rq[1] " " short(rq[2]))
    }
    j = index(msg, ", client: ")
    if (j > 0) msg = substr(msg, 1, j - 1)
    if (lvl == "warn")       { name = "WARN";  col = YELLOW }
    else if (lvl == "error") { name = "ERROR"; col = RED BOLD }
    else if (lvl ~ /crit|alert|emerg/) { name = "CRIT"; col = RED BOLD }
    else                     { name = "INFO";  col = "" }
    printf "%s %s  %s %s%s\n", c(DIM, clock), c(col, pad(name, 5)), c(NGX, pad("nginx", 10)), msg, where
    fflush()
    next
}

# Anything else (shouldn't happen): pass through untouched.
{ print; fflush() }
