# 1. YAML as the source of truth for reader configuration

Status: accepted

## Context

A FEIG reader stores its own settings in EEPROM. Until now an operator set them by
hand through ISOStart or the reader's web interface, and `config.yaml` described what
this service expected to find. Nothing checked that the two agreed.

When they disagree the failure is silent. A reader left in host mode never pushes
notifications, so a gate simply reports nothing; an antenna at the wrong output power
reads some tags and not others. Both look like hardware faults and are diagnosed as
such, expensively.

Two properties of the domain shape the solution. Reader generations name the same
settings differently, so no single list of parameter names covers the fleet. And some
settings must never be written from here: changing a reader's IP address makes it
unreachable, and changing its credentials can lock everyone out permanently, with only
the vendor able to recover it.

## Decision

For readers whose `config.yaml` entry names a generation, the YAML is the source of
truth. The service reads the reader's settings, compares them, and writes back what
differs.

- **Drift is per parameter, not per reader.** A parameter whose value on the reader
  differs from what the profile asks for has *drifted*. Comparison and repair happen one
  parameter at a time, so a reader with one wrong value has one parameter rewritten,
  not all of them.
- **Profiles, composed.** A profile supplies the parameter list for a generation. The
  generations are one class configured twice, differing in a parameter-name prefix, a
  list of field names, where they keep the notification target, how they encode antenna
  output power, and two capability flags. Anything a generation does differently belongs
  on the profile rather than in a branch: hardware probing found the notification target
  and the output power encoding to be generation-specific after both had been written as
  shared constants, and each failed silently on the generation it did not fit.
- **Values carry their type.** A parameter's declared value and the value read back
  from a reader are the same type, built from one declaration, so the two are always
  comparable.
- **Managed is opt-in.** A reader without a `type` is left alone entirely.
- **A denylist, not care.** Network identity and credential parameters are refused by
  the engine and again at the SDK boundary, in tests as in production.

Settings drift back on their own: an operator changes something at the reader, or a
reader is replaced. Synchronisation therefore runs whenever the service connects to a
reader, not once at install time.

## Consequences

A reader that is already correct is not written to, so a restart is cheap and leaves
the hardware alone. What drifted is named in the log and over the API, which is the
diagnostic that did not exist before.

Correctness now depends on the parameter names in each profile being right for the
hardware. A wrong name is not a silent no-op — an unsupported parameter fails naming
the reader and the parameter — but it can only be confirmed against a physical reader.

Writing to a reader is a real side effect on shared hardware. Non-persistent
synchronisation exists for that reason, and the denylist is deliberately broader than
strictly necessary.

**Non-persistent synchronisation does not work, on either generation.** Measured by
writing a value, pulling the power, and reading it back: an MRU400 refuses the apply with
`-114` and keeps the value; an MRU102 answers `ok` and keeps it too. The MRU102 is the
worse case, because nothing reports a problem.

The likely mechanism is that `changeConfigPara` writes through to non-volatile storage
immediately, so the value is already permanent before `apply` is called at all, and the
flag passed to `apply` cannot undo it. That fits both readers: on the MRU400 the value
stuck even though the apply failed outright.

So `readerConfigurationPersistent: false` is not a safety mechanism. The apply logs a
warning whenever it is asked for, and still fails loudly on `-114` — by then the writes
have landed and the operator needs to know that, not just that something went wrong. Only
the denylist is a real guarantee: it refuses parameters before anything is written and
does not depend on the reader honouring anything.

Host mode is not at parity, and the vendor manual says it cannot be: the MRU400X
documents no `OperatingMode.HostMode.*` subtree at all, and the reader reports every
such name as absent. Host mode synchronises the operating mode and the antenna settings
because those are the only parameters it has. What it does not cover — the notification
target and the data selectors — is notification-mode configuration by nature, not a gap
to close.

Reader parameters carry units the SDK does not express: `TransponderValidTime` counts in
100 ms steps, `PersistenceResetTime` in 5 ms. Durations are therefore paired with their
step in the profile and converted where they are used, because all three of these were
once written as bare numbers under comments claiming seconds, and were wrong by factors
of ten and two hundred without anything failing.

## Alternatives considered

**Validate only, never write.** Report disagreement and refuse to start. This was the
shape the original contribution offered as a fallback. Rejected because it turns every
drift into an outage needing a person with ISOStart on site, which is the cost the
feature exists to remove. Reporting without repair is what the engine's `check` does,
though nothing in the service calls it today; a reader's standing is exposed as
`syncState` over the readers API.

**Capability-driven synchronisation with no profiles.** Ask the reader which parameters
it supports and write every one this service knows about. Rejected because supporting a
parameter says nothing about what it should hold — the desired values still have to
come from somewhere — and because it makes the set of things written to a reader depend
on the reader's firmware rather than on anything reviewable.

**A subclass per reader model.** The original contribution took this route with an
`MRU400ReaderConfig extends ReaderConfig`. Rejected because the subclass overrode
`getType()` to a constant and ignored the field it inherited, and because what actually
varies between models is data — names, flags, a list of fields — not behaviour. Every
new model would have meant a new class duplicating the last one.
