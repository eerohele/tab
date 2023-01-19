# Tab

Tab is a little program that turns Clojure data structures into tables.

<img width="1552" alt="Screenshot 2023-01-17 at 21 05 06" src="https://user-images.githubusercontent.com/31859/212988791-5e3a060a-2072-47ba-ba16-2981823ccb2b.png">

> **Note**
> Tab is in **alpha**. While in alpha, breaking changes can happen.

## Rationale

Tab's primary aim is to help Clojure programmers make sense of data.

Most interesting Clojure values are maps or seqs of maps. When small, pretty-printing is an adequate tool for inspecting such values. When they get to medium size (like, say, your typical [Ring](https://github.com/ring-clojure/ring) request map), you're better off reaching for something else.

Tab aims to be that something else.

## Differences to similar tools

- Tab only shows you one value at a time.
- Tab datafies (via `clojure.datafy/datafy`) values you give it.
- Tab aims to be useful without forcing you to choose between different viewers (table, tree, etc).
- Tab has no dependencies.

## Try

Given that you have the [Clojure CLI](https://clojure.org/guides/install_clojure) installed, on the command line, run:

```bash
clj -Sdeps '{:deps {io.github.eerohele/tab {:git/url "https://github.com/eerohele/tab.git" :git/sha "3add14db594806b7a44b2f74eab3b3b80dc1575c"}}}'
```

Then, in the REPL:

```clojure
user=> (require 'tab.auto)
Tab is listening on http://localhost:57426
nil
user=> (tap> BigInteger)
true
```

Then, bask in the glory of the table that appears in your browser. If a tabulated `BigInteger` doesn't do it for you, there are [more examples available](#examples).

> **Note**
> In general, you'll probably want to use [`tab.api`](#api) instead of `tab.auto`. The only purpose of the `tab.auto` namespace is to make it as easy as possible to run a Tab.

See the [user manual](#user-manual) for instructions of use.

To stop Tab:

```clojure
user=> (tab.auto/halt)
```

The `tab.auto` namespace is the easiest way to run Tab. The `tab.api` namespace exposes the [API](#api) proper.

## API

Most importantly, there's `tab.api/run` and `tab.api/halt`:

```clojure
user=> (require '[tab.api :as tab])
nil
;; - Run a Tab in port 1234
;; - Don't open a browser by default.
;; - Set print length to 8
;; - Set print level to 2
user=> (def tab (tab/run :port 1234 :browse? false :print-length 8 :print-level 2))
#'user/tab
user=> (tab/halt tab)
nil
```

See also:

```clojure
user=> (require '[tab.api :as tab])
nil
user=> (doc tab/run)
...
user=> (doc tab/tab>)
...
user=> (doc tab/address)
...
user=> (doc tab/halt)
...
```

## User manual

- Click `-` to collapse a node
- Click `+` to expand a node
- Press <kbd>Alt</kbd> and click `-` or `+` to expand or collapse all nodes underneath a node

## Examples

For more examples on what Tab can do, see [`repl/demo/intro.repl`](https://github.com/eerohele/tab/blob/main/repl/demo/intro.repl).

> **Note**
> To run through all the examples, you must use the [`add-lib3` branch of tools.deps](https://github.com/clojure/tools.deps/tree/add-lib3).

## Features

- Can make tables.
- Zero dependencies, apart from Clojure itself.
- [Prefers operating system theme](https://developer.mozilla.org/en-US/docs/Web/CSS/@media/prefers-color-scheme) (light or dark mode).

## Limitations

- Can only make tables.
- Will blow up expand an infinite seq of scalars a table cell with an ellipsis.
- Requires a modern browser to look good. If your browser supports [`backdrop-filter`](https://developer.mozilla.org/en-US/docs/Web/CSS/backdrop-filter), you should be good.
- Might not work with Clojure versions older than 1.10.2.

## Inspiration

- Gregoire, Daniel. (2018, November 30). [Tables Considered Helpful](https://www.youtube.com/watch?v=b5UK-VHbJlQ) (Clojure/conj 2018).

## Prior art / superior alternatives

- [REBL](https://docs.datomic.com/cloud/other-tools/REBL.html)
- [Portal](https://djblue.github.io/portal/)
- [Reveal](https://vlaaad.github.io/reveal/)

## Acknowledgements

- [My employer](https://www.solita.fi), for graciously sponsoring the development of this tool.
- [Nikita Prokopov](https://github.com/tonsky), for the [Alabaster Color Scheme](https://github.com/tonsky/sublime-scheme-alabaster).
- [Pedro Girardi](https://github.com/pedrorgirardi), for alpha testing and improvement ideas.
