# Reviews written while this lived here

The reviews below were written when following a pull request was a bench
command. It is not one any more: it needs one API read and a record, nothing
that forks a JVM, so it moved into the core where it works against any
repository rather than only this one.

```bash
oss followup                     every reviewed pull request, one line each
oss followup --since <n>         what the author pushed since you reviewed
oss hub                          is anyone waiting on you
```

**New reviews are written to `~/.oss-cli/reviews/`,** and that is where the
ledger lives now. `oss` reads and writes there.

## Why these files are still here

They are kept, not migrated away, because this directory is under version
control and a home directory is not. Fifteen write-ups and the head SHA each
pull request was reviewed at are the one thing in this workflow that cannot be
re-derived from anywhere — losing them to an untracked folder on one laptop
would be a bad trade for tidiness.

So this is an archive: read it, do not add to it. It is also the copy that
seeded `~/.oss-cli/reviews/ledger.tsv`, gaining a `repo` column on the way, so
that "PR 4234" still means something once you follow more than one project.

None of these reviews has been posted upstream. That remains a decision a person
makes by hand, in their own words.
