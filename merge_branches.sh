#!/bin/bash
set -e

cd /c/Users/luned/Documents/Projects/mimiral

BRANCHES=(
  "kanban/t_9ce7606f"
  "kanban/t_21c471b6"
  "kanban/t_c50b7f73"
  "kanban/t_b39d98bd"
  "kanban/t_b17ddd2c"
  "kanban/t_268e279e"
  "kanban/t_4c8bbd40"
  "kanban/t_eddd19de"
  "kanban/t_c7d4bdf5"
  "kanban/t_00588201"
  "kanban/t_7e65cfad"
)

SUCCESS=()
FAILED=()

for branch in "${BRANCHES[@]}"; do
  echo "========================================"
  echo "Merging $branch..."
  
  if git merge --no-ff "$branch" -m "Merge branch $branch" 2>&1; then
    echo "  -> Clean merge"
    SUCCESS+=("$branch")
  else
    echo "  -> Conflicts detected, resolving..."
    conflicted=$(git diff --name-only --diff-filter=U)
    
    if [ -z "$conflicted" ]; then
      echo "  -> No conflicted files but merge failed. Aborting this merge."
      git merge --abort 2>/dev/null || true
      FAILED+=("$branch")
      continue
    fi
    
    for f in $conflicted; do
      echo "    Resolving: $f"
      git checkout --theirs "$f"
      git add "$f"
    done
    
    git add -A
    
    remaining=$(git diff --name-only --diff-filter=U)
    if [ -n "$remaining" ]; then
      echo "  -> Still unmerged: $remaining"
      git merge --abort 2>/dev/null || true
      FAILED+=("$branch")
      continue
    fi
    
    if git commit -m "Merge branch $branch (conflicts resolved: branch version preferred)"; then
      echo "  -> Committed"
      SUCCESS+=("$branch")
    else
      echo "  -> Commit failed"
      git merge --abort 2>/dev/null || true
      FAILED+=("$branch")
    fi
  fi
done

echo ""
echo "========================================"
echo "RESULTS"
echo "Success: ${#SUCCESS[@]}"
for b in "${SUCCESS[@]}"; do echo "  OK: $b"; done
echo "Failed: ${#FAILED[@]}"
for b in "${FAILED[@]}"; do echo "  FAIL: $b"; done
