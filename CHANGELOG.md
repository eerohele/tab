# Changelog

All notable changes to this project will be documented in this file.

## UNRELEASED

- Sort maps and seqs of maps by key #9
- Show expand/collapse controls when seq of maps exceeds `:print-length` #7
- Reduce memory use and improve performance
- Use custom pprint impl instead of `clojure.pprint/pprint`

  `tab.impl.pprint/pprint` is hundreds of times faster than `clojure.pprint/pprint` with some payloads, and at least 10x faster for most payloads. It is 12-15x faster than `fipp.edn/pprint` at Fipp's own benchmark.

  Furthermore, `tab.impl.pprint/pprint` allocates 25x fewer bytes than `clojure.pprint/pprint` and 10x fewer bytes than `fipp.edn/pprint`.

- Ensure Tab respects `:print-length nil` and `:print-level nil` even
  when `*print-length*` and `*print-level*` are set

## 2023-05-03

- Fix Chrome UI freeze on back/forward navigation #5

## 2023-04-14

- Fix toggling map keys that are maps #4

## 2023-01-28

- Improve support for back/forward browser navigation
- Redesign history retention

  Tab now retains values in an in-memory database until you manually clear
  them.

- Use relative instead of absolute time in the UI
- BREAKING: `:max-vals` arg to `tab.api/run` no longer has any effect
- Add missing docstrings
- Minor UI improvements

## 2023-01-21

- Add support for [zooming in on values](https://github.com/eerohele/tab#user-manual)
- Add support for [copying values to clipboard](https://github.com/eerohele/tab#user-manual)
- Add support for [navigating to undatafied objects](https://github.com/eerohele/tab#user-manual)
- UI improvements

## 2023-01-20

- Don't datafy nested values

- Datafy values earlier to support ephemeral objects

  Prior to this change, Tab datafied values upon rendering rather than when receiving the value. If the datafied object was ephemeral (such as a HTTP response object), Tab errored out when refreshing a page that showed the value.

## 2023-01-19

- Add default values for `:print-length` (`8`) and `:print-level` (`2`) options

- Fetch nested data on demand rather than eagerly

  This allows for sending Tab arbitrarily deeply nested data structures.

- Datafy nested values

## 2023-01-18

- Initial release
