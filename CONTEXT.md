# Context

This service talks to FEIG RFID readers on a library's LAN and turns tag reads into
HTTP responses and server-sent events. It also keeps each reader's own settings in
line with `config.yaml`.

Tag vocabulary lives with the code in `src/main/java/de/bookwaves/tag/`; the terms
below are the ones the configuration side uses.

## Readers

**Reader generation.** FEIG readers of different vintages describe the same concepts
under different parameter names. Two generations matter here:

- **NewGen** — current UHF hardware, for example the MRU400X. Has user login, exposes
  persistence reset, keeps notification settings under `OperatingMode.AutoReadModes.*`,
  and reaches 1.0 W of antenna output power.
- **OldGen** — earlier UHF hardware, for example the MRU102. No login, no persistence
  reset, the same settings live under `OperatingMode.NotificationMode.*`, and its full
  output power is 0.5 W.

The differences are not only naming: what a generation *can store* differs too, so a
value valid for one may be unreachable on the other.

HF readers are a third kind, currently unmanaged.

**Host mode and notification mode.** In *host mode* this service asks the reader for
tags and the reader answers. In *notification mode* the reader dials back to this host
on its own and pushes reads as they happen, which is what a gate or a self-checkout
needs. The reader's `OperatingMode.Mode` parameter selects between them, and
notification mode additionally needs the reader to know this host's address and port.

## Configuration

**Managed and unmanaged.** A reader's `type` in `config.yaml` decides whether this
service touches its settings. The default, `GENERIC`, means *unmanaged*: the service
talks to the reader but leaves its configuration exactly as an operator set it. A
`type` naming a generation makes the reader *managed*.

**Profile** (`sync/ReaderProfile`). What a generation's settings are called and which
of them exist. Given a reader's YAML entry, a profile produces the full list of
parameters that reader should hold. `sync/ReaderProfiles` holds the instances.

**Parameter descriptor** (`sync/ParamSpec`). One parameter a profile wants: its name on
the reader, the value it should hold, and a short purpose used in log messages. The
value is a `ParamValue` — a type the reader understands, not a bare Java primitive — so
that a desired value and a value read back from a reader are always the same type and
can be compared directly.

**Notification target** (`sync/NotificationTarget`). The three parameter *names* — not
values — by which a generation is told where to dial back to: an address, a port, and a
connection hold time. The generations keep these in unrelated subtrees, so a profile
carries the set that fits its hardware.

**Output power codec** (`sync/OutputPowerCodec`). The translation between the fractional
watts written in `config.yaml` and the code a reader stores. Each generation has its
own, because they reach different maximum powers; a power is therefore only valid
against the profile of the reader that has to store it.

**Drift.** A parameter whose value on the reader differs from what the profile asks
for. Drift is per parameter, not per reader: a reader with one wrong antenna power has
drifted in exactly one parameter, and only that parameter is rewritten.

**Synchronisation** (`sync/ConfigurationSync`). Reading every parameter a profile
declares, comparing each against its descriptor, and writing back only those that
drifted. `check` reports; `apply` reports and repairs. A reader's current standing is
its `SyncState`: `UNMANAGED`, `NEVER_CHECKED`, `UNREACHABLE`, `IN_SYNC` or `DRIFTED`.

**Persistent and non-persistent.** Applying a change persistently writes it to the
reader's EEPROM, so it survives a power cycle. Non-persistent changes are lost on
restart, which makes them the safe choice while working out settings for a site.
`readerConfigurationPersistent` in `config.yaml` chooses; it defaults to persistent.

**Protected parameters** (`sync/ProtectedParameters`). Settings synchronisation refuses
to write under any circumstances: the reader's own network identity and anything to do
with credentials or access protection. Getting these wrong makes a reader unreachable,
or locks everyone out of it permanently.

## Talking to the SDK

`sync/ReaderConfigPort` is the only route to reader configuration, and
`FeigReaderConfigPort` its only real implementation. Everything else in `sync/` is
plain Java with no SDK types in it, which is why the synchronisation logic can be
tested without a reader present. `ReaderProbe` reports what a physical reader actually
supports, for the questions unit tests cannot answer.
