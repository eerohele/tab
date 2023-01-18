# Tab

Tab turns Clojure data structures into tables.

<img width="1552" alt="Screenshot 2023-01-17 at 21 05 06" src="https://user-images.githubusercontent.com/31859/212988791-5e3a060a-2072-47ba-ba16-2981823ccb2b.png">

> **Note**
> Tab is in **alpha**. While in alpha, breaking changes can happen.

## Rationale

Tab's primary aim is to help Clojure programmers make sense of data.

Most interesting Clojure values are maps or seqs of maps. When small, pretty-printing is an adequate tool for inspecting such values. When they get to medium size (like, say, your typical [Ring](https://github.com/ring-clojure/ring) request map), you're better off reaching for something else.

Tab aims to be that something else.

## Differences to similar tools

- Tab only shows you one value at a time.
- Tab datafies (via `clojure.datafy/datafy`) everything you give it.
- Tab aims to be useful without forcing you to choose between different viewers (table, tree, etc).
- Tab has no dependencies.

## Try

Given that you have the [Clojure CLI](https://clojure.org/guides/install_clojure) installed, on the command line, run:

```bash
clj -Sdeps '{:deps {io.github.eerohele/tab {:git/url "git@github.com:eerohele/tab.git" :git/sha "3e23ce61ca2e221a87838f95b993c26bec47c5e4"}}}'
```

Then, in the REPL:

```clojure
user=> (require 'tab.auto)
nil
user=> (tap> BigInteger)
true
```

Then, bask in the glory of the table that appears in your browser. There's also more [examples](#examples) if a tabulated `BigInteger` doesn't do it for you.

To stop Tab:

```clojure
user=> (tab.auto/halt)
```

The `tab.auto` namespace is the easiest (in the near-at-hand sense) way to run Tab. The `tab.api` namespace exposes the [API](#api) proper.

## API

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
- Likely to die if given a very large input.
- Eager; will blow up if you give it an infinite seq.
- Requires a modern browser to look good. If your browser supports [`backdrop-filter`](https://developer.mozilla.org/en-US/docs/Web/CSS/backdrop-filter), you should be good.

## Inspiration

- Gregoire, Daniel. (2018, November 30). [Tables Considered Helpful](https://www.youtube.com/watch?v=b5UK-VHbJlQ) (Clojure/conj 2018).

## Prior art / superior alternatives

- [REBL](https://docs.datomic.com/cloud/other-tools/REBL.html)
- [Portal](https://djblue.github.io/portal/)
- [Reveal](https://vlaaad.github.io/reveal/)

## Acknowledgements

- [My employer](https://www.solita.fi), for graciously sponsoring the development of this tool.
- Nikita Prokopov, for the [Alabaster Color Scheme](https://github.com/tonsky/sublime-scheme-alabaster).
- [Pedro Girardi](https://github.com/pedrorgirardi), for alpha testing and improvement ideas.
