# Conventions

Applies to everything in this repository, to agents and people alike.

## Comments and documentation

Document what the code is and why it is that way. Never document what it used
to be.

Do not write:

- that something was moved, renamed, replaced, or now lives elsewhere
- that a class or method does *not* do a thing, when the only reason to mention
  it is that an earlier version did
- changelog entries, migration notes, or "formerly X" asides
- a defense of the current design against an alternative that was never shipped

A doc comment is read by someone meeting the code for the first time, with no
memory of any earlier version of it. Write for them. The history is in
`git log`.

This is not a ban on rationale. Why a value is clamped, why an injection point
was chosen, why an invariant has to hold, what breaks if it stops holding — that
is what the code is and why, and it stays. So does a statement about runtime
state, including a negative one: "a type with nothing left under it would
promise saved presets it no longer has" is about the program, not about the
commit that changed it.

## American English

Spell in American English, in prose and identifiers alike: color, behavior,
gray, initialize, canceled, center.

Either spelling would have done. Having both is what costs: a name you cannot
guess, and a grep you have to run twice.

Names that belong to something else keep their own spelling. Beta's fields and
methods are what they are, and so is anything a mod already calls by a British
name.

## Breaking a published API

The rule above is about code that nobody outside this repository compiles
against. An API someone else builds on is the exception, and not really a
contradiction: there, what replaced what is not history but the current shape of
the contract, and the person reading it is usually the person whose build just
broke.

For any type, method or field another mod could reasonably be compiling against:

- Do not rename, move or delete it quietly. Deprecate it first, and say in the
  `@deprecated` tag what to use instead. A deprecation with no forwarding
  address is worse than none.
- Keep the deprecated symbol working for as long as the deprecation says it
  will.
- The replacement documents itself, not its predecessor. "Formerly `Foo`" on the
  new symbol is exactly the pollution the first rule is about.
- Once the old symbol is actually gone, its note goes with it. Javadoc on
  something that no longer exists cannot be read by anyone, and the migration
  belongs in the release notes.

## Reaching private game code

In order of preference:

1. Something the loader or StationAPI already exposes. The loader's game
   instance rather than Beta's client singleton; StationAPI's own registries and
   events rather than the fields behind them.
2. A mixin accessor or invoker.
3. An access widener, and only with a reason that the first two cannot meet.

An accessor is scoped, named and typed, it can give a raw field a real signature
at the one place that knows what belongs in it, and it sits with the other
mixins that already describe what this mod reaches into. A widener opens the
member to the whole compilation and records nothing about who wanted it.

Wideners are worth it when the point *is* to open something up for everyone:
StationAPI's transitive wideners exist so mods can construct blocks and items,
which is a deliberate API affordance. That is not the same as getting at a
private field, and a widener that is not transitive does not propagate anyway.

## Commits and branches

Commit subjects follow [Conventional Commits](https://www.conventionalcommits.org):
`type: description`, lowercase after the colon, no trailing period. The types
are `feat`, `fix`, `docs`, `refactor`, `perf`, `test`, `build`, `ci`, `style`
and `chore`; a breaking change is `type!:`. A scope in parentheses is allowed
and rarely worth it.

The subject completes "if applied, this commit will ___", so it is `add` and
not `added`. Keep it short and put the reasoning in the body, which is free
prose and has no format. The subject is not where to be thorough.

`master` holds released code and `develop` is where work lands, so a release is
a merge of `develop` into `master`. Everything else is a short lived branch off
`develop`, named `feature/what-it-adds` or `fix/what-it-fixes`, merged by pull
request.

A fix that has to reach a released version before `develop` is ready branches
off `master` instead, and merges into both. Otherwise `master` and `develop`
drift apart and the next release quietly reverts it.
