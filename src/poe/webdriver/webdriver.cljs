(ns poe.webdriver.webdriver
  "A namespace to expose the objects from selenium-webdriver,
  in order to require them from npm dep once and have access
  throughout."
  (:require [goog.object :as go]))

;; node dependencies

(def webdriver (js/require "selenium-webdriver"))
(def Builder (go/get webdriver "Builder"))
(def By (go/get webdriver "By"))
(def Key (go/get webdriver "Key"))
(def until (go/get webdriver "until"))

;; convenience functions

(defn by*
  "Call the identified function named by by-fn-keyword, passing
  selector to that function

  This allows a more clojure-friendly usage of `By` functionality:
  (by* :css \".class-selector\")
  (by* :xpath \"//input[@name='password']\")"
  [by-fn-keword selector]
  ((go/get By (name by-fn-keword)) selector))
