(ns poe.file-export
  (:require [poe.util :as util]))

(def csv-stringify (js/require "csv-stringify/lib/sync"))

(defn csv
  [file rows]
  (util/spit file (csv-stringify (clj->js rows))))
