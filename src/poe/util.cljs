(ns poe.util
  "Common convenient functions"
  (:require [cljs.reader :as cljs-reader]))

(def fs (js/require "fs"))
(def csv-stringify (js/require "csv-stringify/lib/sync"))

(defn slurp
  "Read a file, as a string"
  [path]
  (str (.readFileSync fs path)))

(defn slurp-edn
  "Read a file, as edn"
  [path]
  (-> path
      slurp
      cljs-reader/read-string))

(defn spit
  "Write a string to file"
  [f data]
  (.writeFileSync fs f data))
