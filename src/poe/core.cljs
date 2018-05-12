(ns poe.core
  "Provides a test runnter to make it easier to work with
  selenium-webdriver, with a focus on data and direct interop."
  (:require [goog.object :as go]
            [poe.webdriver.webdriver :as webdriver]))

(def driver
  "The selenium-webdriver driver to be used for a session"
  ;; TODO: Research what options are for this part of things...
  (-> (webdriver/Builder.)
      (.forBrowser "chrome")
      (.build)))

(def default-timeout
  "The default timeout for selenium-webdriver operations"
  3000)

(defn by*
  "Call the identified function named by by-fn-keyword, passing
  selector to that function

  This allows a more clojure-friendly usage of `By` functionality:
  (by* :css \".class-selector\")
  (by* :xpath \"//input[@name='password']\")"
  [by-fn-keword selector]
  ((go/get webdriver/By (name by-fn-keword)) selector))

(defmulti interpret
  "Interpret an action as a selenium-webdriver operation.
  Meant to allow common actions to be easily specified.

  A couple of examples for the out-of-the-box actions are:

  [:click \"#element-of-interest\"]
  [:input-text \"#name-input\" \"Sterling Archer\"]

  As the names suggest, the first vector will allow you to
  click on an element based on a css selector while the
  second will find an input an type text into it.

  `interpret` is meant to allow common testing patterns to be
  captured and easily specified in order to create a simple
  way to sequence webdriver operations."
  (fn [[action & _]] action))

(defmethod
  ^{:doc "Call the selenium-webdriver `.quit` function, close the browser."}
  interpret :quit
  [[action]]
  #(.quit driver))

(defmethod
  ^{:doc "Wait for an arbitrary `condition` to be true."}
  interpret :wait
  [[action condition {:keys [timeout]}]]
  #(.wait driver condition (or timeout default-timeout)))

(defmethod
  ^{:doc "Wait until the element identified by `selector` becomes visible."}
  interpret :wait-until-visible
  [[action selector {:keys [timeout by]
                     :or   {by :css}}]]
  #(.wait driver
          (.elementIsVisible webdriver/until (.findElement driver (by* by selector)))
          (or timeout default-timeout)))

(defmethod
  ^{:doc "Wait until the element indentified by `selector` is added to the DOM."}
  interpret :wait-until-located
  [[action selector {:keys [timeout by]
                     :or   {timeout default-timeout
                            by      :css}}]]
  #(.wait driver
          (.elementLocated webdriver/until (by* by selector))
          (or timeout default-timeout)))

(defmethod
  ^{:doc "Wait until the element identified by `selector` becomes stale."}
  interpret :wait-for-stale
  [[action selector {:keys [timeout by]}]]
  #(.wait driver
          (.stalenessOf webdriver/until (.findElement driver (by* by selector)))
          (or timeout default-timeout)))

(defmethod
  ^{:doc ":click - Click the element identified by `selector`."}
  interpret :click
  [[action selector {:keys [by]
                     :or   {by :css}}]]
  #(-> (.findElement driver (by* by selector))
       (.click)))

(defmethod
  ^{:doc ":input-text - Input `text` into the element identified by `selector`."}
  interpret :input-text
  [[action selector text {:keys [enter? by]
                          :or {enter? false
                               by     :css}}]]
  #(-> (.findElement driver (by* by selector))
       (.sendKeys text (if enter? webdriver/Key.ENTER ""))))

(defn run
  "Run the vector of step-fns as a series of chained `then` calls,
  starting after a call to the driver with url, `(.get driver url)`.

  Each element of `step-fns` can be either a function or a vector.

  Any elements of `step-fns` that are functions are used to chain
  to the previous `step-fns`, by `then`ing them together.

  The following function is an example of raw interop used to locate
  an input element with the class \"comment-input\". It then writes
  the value \"text for input\".

  #(.-> (.findElement driver (by* :css \".comment-input\"))
        (.sendKeys \"text for input\"))

  Any elements of `step-fns` that are vectors will be interpreted,
  see `defmulti interpret`.

  To accomplish the same end as the above example, you could instead
  pass a data structure, and for this specific example that would look
  like:

  [:input-text \".comment-input\" \"text for input\"]

  Notice that this allows you to write less code.

  The expectation is that `run` doesn't want to get in the way of
  specifying every possible underlying call to selenium-webdriver,
  but if you find yourself using certain ones over and over again,
  you can extend `interpret` to give yourself a more convenient way
  to express the operation."
  [{:keys [url quit?] :or {quit? true}} step-fns]
  (.catch
   (reduce (fn [acc cur]
             (.then acc (if (vector? cur)
                          (interpret cur)
                          cur)))
           (.get driver url)
           (cond-> step-fns
             quit? (into [[:quit]])))
   ;; TODO: May want to allow more options for how to handle errors
   ;; NOTE: The reporting of errors is brief, and at times it is
   ;;       borderline unhelpful. This is not because of lumo...
   #(do
      (js/console.log "Exception occurred:")
      (js/console.log %)
      (.exit js/process 1))))

;; Example of raw interop with selenium-webdriver, for comparison
(comment
  (-> (.get driver "http://www.google.com/ncr")
      (.then #(-> (.findElement driver (.name webdriver/By "q"))
                  (.sendKeys "webdriver" webdriver/Key.ENTER)))
      (.then #(.wait driver (.titleIs webdriver/until "webdriver - Google Search") 1000))
      (.then #(.quit driver))
      (.catch #(do
                 (js/console.log "Exception occurred:")
                 (js/console.log %)
                 (.quit driver)
                 (.exit js/process 1)))))

;; Example of using interpretation alone to login to hotmail
(comment
  (let [url               "https://outlook.live.com/owa/"
        email             "fake-email-address@hotmail.com"
        password          "fake-password"
        signin-selector   ".office-signIn"
        email-selector    "input[type='email']"
        password-selector "//input[@name='passwd'and @type='password']"]
    (run
      {:url   url
       :quit? false}
      [[:click signin-selector]
       [:wait-until-visible email-selector]
       [:input-text email-selector email {:enter? true}]
       [:wait-for-stale password-selector {:by :xpath}]
       [:wait-until-visible password-selector {:by :xpath}]
       [:input-text password-selector password {:enter? true :by :xpath}]])))
