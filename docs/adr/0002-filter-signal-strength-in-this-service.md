# 2. Filter signal strength in this service, not on the reader

Status: accepted

## Context

A reader that hears tags too far away reports books on the next shelf, or a gate
reports items that are merely nearby. The reader has its own remedy: a per-antenna RSSI
filter (`AirInterface.Antenna.UHF.NoX.RSSIFilter`, 0…255 in −dBm) that suppresses weak
reads before they are reported. `rssiFilters` in `config.yaml` has carried those values,
and synchronisation (ADR 1) writes them to managed readers.

Field testing found the reader's filter unreliable: with it active, reads of tags well
inside the intended range become unstable. With it switched off and the same minimum
applied after the fact — the approach an earlier application of ours already used —
reads were markedly more stable.

The notification path also carried a stability gate, `bestRssi <= 39`, written for a
positive "−dBm" convention. Assuming the SDK always reported signed dBm (−42, −70)
led to a software filter that accepted every positive reading. A near → far → near
experiment on an MRU102 showed positive magnitudes: 28 near, 55 far, then 28 near.
Smaller positive values mean stronger reception on that reader.

## Decision

This service applies the per-antenna minimum to every tag read, in host mode and in
notification mode, after the reader has reported it.

- **One filter at a time.** A reader's `rssiFilterMode` is `software` or `reader`. In
  `software` mode synchronisation writes 0 (off) to every configured antenna's reader
  filter, drift-checked like any other parameter, and this service filters. In `reader`
  mode the values go to the reader as before and nothing is filtered here.
- **`software` is the default.** The reader's filter is known to misbehave; an opt-in
  fix would rarely be turned on. Upgrading therefore switches the reader filter off on
  managed readers at their next connection.
- **Same values, same units.** `rssiFilters` keeps its meaning — positive, in −dBm, 0 for
  none — so existing site configuration carries over unchanged. Comparisons normalize
  positive RSSI magnitudes to negative dBm and leave negative values unchanged. Filtering
  and strongest-antenna selection use that same comparison. API fields retain raw RSSI;
  `bestRssi` is the raw value of the strongest reading, not the numerically largest value.
- **Per antenna reading, then per tag.** A reading weaker than its antenna's minimum is
  discarded; a tag is discarded only when none of its readings survive. The minimum is
  inclusive: at 60, a −60 dBm reading is kept.
- **Unjudgeable reads pass.** A reading with no valid signal strength is not judged: it
  cannot save a tag whose other readings fail, and a tag with only such readings is let
  through, with a warning, rather than silently dropping everything a misconfigured
  reader reports.
- **Discarded means unseen.** In notification mode a discarded read does not refresh a
  tag's presence or count towards stability, so a tag that leaves the intended range is
  removed by the ordinary presence timeout, and it cannot be the target of a write. The
  no-op `bestRssi <= 39` gate is removed.
- **Every inventory.** The filter applies wherever the reader's filter used to act,
  including the inventories inside initialise, edit and secure, so a weak neighbouring
  tag cannot trip "more than one tag in the field".
- **Unmanaged readers too.** A `GENERIC` reader with `rssiFilters` is filtered in
  software, but this service does not touch its settings, so its own filter must be
  switched off by hand.

## Consequences

The filter is enforced by code that can be unit tested without a reader, and what it
discards is visible in the debug log and summarised per inventory.

The reader now reports and transmits every tag it hears, which is more traffic and more
work for this service. At library scale this is negligible.

A tag at the edge of range alternates between kept and discarded reads. No hysteresis is
applied; the one-second presence timeout absorbs the flicker. Revisit if gates report
tags flapping between stable and removed.

## Alternatives considered

**Keep filtering on the reader.** Rejected on the testers' measurements; it remains
available per reader as `rssiFilterMode: reader`.

**A separate key for the software-defined filter.** Rejected: the numbers mean exactly
the same thing, and two lists invite configurations where both filters are active at once.

**Default to `reader`.** Rejected: it preserves the known-bad behaviour on every site
that does not read the release notes.
