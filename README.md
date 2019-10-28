# poe

A lumo based test runner for selenium-webdriver using node-js bindings
and clojurescript

## Motivation

I wanted to play with the node-js bindings for selenium-webdriver, and
have used lumo with success in the past to leverage npm via
clojurescript.

The clojure libraries I've seen that integrate with selenium have
focused on providing api functions, either through reflection or
direct specification, so that you have access to them in an idiomatic
way.

Though this is useful, I didn't want to create a bindings library, and
instead wanted to focus on direct interop to discover useful patterns,
and then capture those patterns and allow them to be leveraged as
interpreted data.

The `poe.core/interpret` multimethod provides a way to capture these
patterns.

The node bindings present an interesting interface for clojure,
because most operations are performed by chaining promises together to
create the sequence of events to test.  This was another area that I
thought could be simplified for test specification.

The `poe.core/run` function captures a way for a vector of operations
to be used to define and run a test, handling all of the chaining for
you.

## Install

This library depends on selenium-webdriver, and is scoped to expect
the use of chromedriver to operate with google chrome. Not currently
planning to go for more support, unless it becomes important to.

Also, I'm assuming OSX with brew, if you aren't using this, you can
get the dependencies some other way.

```bash
brew cask install chromedriver
brew install lumo
npm install
```

## Examples

There is an example directory that will run out-of-the-box examples to
show you how to get started.

NOTE: If you want the email login examples to succeed, you will need
      to update their text in them to have valid credentials!

```bash
lumo --classpath src:examples -m examples.google-search
lumo --classpath src:examples -m examples.gmail-login
lumo --classpath src:examples -m examples.hotmail-login
lumo --classpath src:examples -m examples.table
```

## How to use this library

- TODO: Discuss ability to use arbitrary functions for operations
- TODO: Discuss how to turn arbitrary function into defmethods for
  interpret
- TODO: Discuss why data format for tests is useful

## Resources

The following should be sufficient to fill in the gaps that I've left:

- [anmonteiro's lumo](https://github.com/anmonteiro/lumo)

- [selenium-webdriver, node
  bindings](http://seleniumhq.github.io/selenium/docs/api/javascript/index.html)
- [Rafal Spacjer's blogpost on clojurescript
  interop](http://www.spacjer.com/blog/2014/09/12/clojurescript-javascript-interop/)
- [clojurescript cheatsheet, search for
  interop](https://cljs.info/cheatsheet/)
