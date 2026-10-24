#!/bin/sh
# ex2_bsub.sh -- the course cluster batch script for the Ex2 cell growth chamber model.
#
# Every batch script must source the module init itself: a non-interactive SSH
# command and an LSF script do NOT inherit the login shell's module environment.
#
# Configuration is passed as POSITIONAL ARGUMENTS to comsolbatch, because the
# COMSOL batch SecurityManager denies file reads/writes and environment
# variables inside the model class:
#
#     comsolbatch -inputfile Ex2Chamber.class -methodcall main <mode> <outdir>
#
# Results are printed to STDOUT, which the scheduler captures into the job log.
#
# Usage (submit from $HOME/a2):
#     bsub < ex2_bsub.sh bc      # parts B + C
#     bsub < ex2_bsub.sh d       # part D
#     bsub < ex2_bsub.sh ef      # parts E + F
# The mode is the FIRST POSITIONAL ARGUMENT of the job script.  It must not
# come from the environment: LSF does not propagate the submitting shell's
# exported variables into the job, so "MODE=ef bsub < script" silently ran
# the job in "bc" mode and made every job write to the same output file.
# ---------------------------------------------------------------------------
#BSUB -J ex2
#BSUB -q hpc
#BSUB -W 8:00
#BSUB -n 8
#BSUB -R "span[hosts=1]"
#BSUB -R "rusage[mem=48GB]"
#BSUB -o ex2_%J.out
#BSUB -e ex2_%J.err
# ---------------------------------------------------------------------------
echo "HOST=$(hostname)"
echo "JOB=$LSB_JOBID"
source /usr/share/Modules/init/bash
module load comsol/6.4 2>&1
unset JAVA_TOOL_OPTIONS

mkdir -p "$HOME/comsoltmp" "$HOME/comsolrecovery"
export TMPDIR="$HOME/comsoltmp"
cd "$HOME/a2" || exit 1
echo "=== running in $(pwd) ==="

# The mode is BAKED INTO THIS SCRIPT.  It must not come from the
# environment (LSF does not propagate the submitting shell's exported
# variables into the job, so "MODE=ef bsub < script" silently ran every job
# in "bc" mode and made every job write to the same output file), and it
# cannot come from a trailing command-line argument either ("bsub < script
# ef" makes bsub treat "ef" as a target file and it then stops parsing the
# #BSUB directives -- the job runs with the default 15 min / 1 GB).
MODE="bc"
RUN="ex2_${MODE}"
echo "MODE = ${MODE}  RUN = ${RUN}"

comsol batch -np 8 -tmpdir "$HOME/comsoltmp" -recoverydir "$HOME/comsolrecovery" \
    -inputfile Ex2Chamber.class -methodcall main "$MODE" "$RUN" > "${RUN}_stdout.log" 2>&1
echo "EXIT=$?"

echo "----- KEY OUTPUT -----"
grep -aE "EX2 MODE|MESH numElem|MESH DIST|EVAL |SERIES|PARAM |TABLE |VOLUME|DIAG|FAILED|Exception|DONE" "${RUN}_stdout.log" | head -200
echo "----- END KEY OUTPUT -----"
wc -l "${RUN}_stdout.log"
ls -la *.csv *.mph 2>&1 | head -20
echo "===== BATCH SCRIPT DONE ====="
