#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# run_comsol.sh -- compile a COMSOL java model and run it in batch, SAFELY.
#
# WHY THIS EXISTS (the stale-class bug):
#   On 2026-10-24 the Ex1 fix (Outflow restricted to the outlet edge) was
#   written to Ex1_HFilter.java, but the compile FAILED with a type mismatch.
#   The old shell command was
#       comsolcompile Foo.java && comsolbatch -inputfile Foo.class ...
#   and `comsolcompile` prints "Failed to compile java file." but STILL RETURNS
#   EXIT CODE 0.  So `&&` happily continued and comsolbatch ran the STALE
#   .class from the previous run.  The result was a "successful" 20-minute run
#   that silently tested the OLD physics, and the log looked plausible.
#
#   This script fails loudly instead:
#     * greps the compiler output for a failure,
#     * requires the .class to be NEWER than the .java,
#     * verifies the expected marker strings are actually inside the .class
#       (a stale class will not contain a marker added to the source),
#     * and only then launches comsolbatch.
#
# Usage:  ./run_comsol.sh <BaseName> <logfile> [marker1 marker2 ...]
#   e.g.  ./run_comsol.sh Ex1_HFilter ../notes/ex1_comsol_run6.log DIAGNOSTIC
# ---------------------------------------------------------------------------
set -u

BASE="${1:?usage: run_comsol.sh <BaseName> <logfile> [marker ...]}"
LOG="${2:?usage: run_comsol.sh <BaseName> <logfile> [marker ...]}"
shift 2
MARKERS=("$@")

COMSOL_BIN="/c/Program Files/COMSOL/COMSOL64/Multiphysics/bin/win64"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$HERE" || exit 1

echo "=== [$(date +%H:%M:%S)] compiling $BASE.java ==="
COMPILE_OUT="$(MSYS2_ARG_CONV_EXCL='*' "$COMSOL_BIN/comsolcompile.exe" "$BASE.java" 2>&1)"
COMPILE_RC=$?
echo "$COMPILE_OUT" | tail -20

# 1) the compiler prints "Failed to compile java file." on error
if echo "$COMPILE_OUT" | grep -q "Failed to compile java file"; then
    echo "!!! COMPILE FAILED - not running, and NOT falling through to a stale class"
    echo "$COMPILE_OUT" | grep -iE "error|ERROR" | head -20
    exit 1
fi
if [ $COMPILE_RC -ne 0 ]; then
    echo "!!! COMPILE returned exit code $COMPILE_RC - aborting"
    exit 1
fi

# 2) the .class must exist and be NEWER than the .java
if [ ! -f "$BASE.class" ]; then
    echo "!!! $BASE.class missing after compile - aborting"
    exit 1
fi
if [ "$BASE.java" -nt "$BASE.class" ]; then
    echo "!!! $BASE.class is OLDER than $BASE.java - stale class, aborting"
    exit 1
fi

# 3) the markers must actually be inside the compiled class.
#    NOTE: do NOT use `strings` here - it is not installed on this Windows host
#    (`command not found`), and a missing tool makes the check fail for the
#    wrong reason.  `grep -a` on the binary works and is what we use.
for m in ${MARKERS[@]+"${MARKERS[@]}"}; do
    if ! grep -aq "$m" "$BASE.class"; then
        echo "!!! marker '$m' NOT found in $BASE.class - the class is stale, aborting"
        exit 1
    fi
    echo "    marker ok: $m"
done

echo "=== [$(date +%H:%M:%S)] compile ok, starting $BASE ==="
MSYS2_ARG_CONV_EXCL='*' "$COMSOL_BIN/comsolbatch.exe" \
    -inputfile "$BASE.class" -methodcall run > "$LOG" 2>&1
BATCH_RC=$?
echo "=== [$(date +%H:%M:%S)] comsolbatch finished, exit code $BATCH_RC ==="
echo "--- log: $LOG ---"
grep -aE "DIAGNOSTIC|c_avg_upper|c_inlet_avg|Ave p|Ave \|U\||DONE|FAILED|Exception" "$LOG" | head -40
exit $BATCH_RC
