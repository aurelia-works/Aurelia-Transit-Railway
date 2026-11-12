#!/bin/sh
# ATR fast check: compile main whenever Java, Gradle or config changed.
# A fresh worktree needs setupFiles once; the main checkout already has it.
BASE=${VERIFY_BASE:-HEAD}
CHANGED=$( (git diff "$BASE" --name-only; git ls-files -o --exclude-standard) 2>/dev/null)
printf "%s\n" "$CHANGED" | grep -qE '\.(java|gradle|properties)$' || exit 0
if [ ! -d fabric/build/generated ]; then
	./gradlew setupFiles -q || exit 1
fi
exec ./gradlew :fabric:compileJava --offline -q
