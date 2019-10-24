(ns examples.aptible
  "An example of how to use poe/run to perform some security scanning of aptible apps

  APTIBLE_EMAIL=fake@email.com APTIBLE_PASSWORD=p@ssW0rD APTIBLE_MULTI_FACTOR_TOKEN=123456 lumo --classpath src:examples -m examples.aptible
"
  (:require [poe.core :as poe]
            [cljs.reader :as cljs-reader]))

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

(def env-email process.env.APTIBLE_EMAIL)
(def env-password process.env.APTIBLE_PASSWORD)
(def env-multi-factor-token process.env.APTIBLE_MULTI_FACTOR_TOKEN)
(def env-output-dir process.env.APTIBLE_OUTPUT_DIR)

;; The aptible UI renders elements with hrefs to ids, not the convenient names
;; of stacks/environments/apps. For this reason, it is most simple to create a
;; mapping of names to ids so that the selectors used in navigation are more
;; reliable
(def cwd (js/process.cwd))
(def stack-name->id-path (or process.env.APTIBLE_STACK_NAME_TO_ID_EDN_FILE
                             (str cwd "/examples/aptible/stack-name-to-id.edn")))
(def environment-name->id-path (or process.env.APTIBLE_ENVIRONMENT_NAME_TO_ID_EDN_FILE
                                   (str cwd "/examples/aptible/environment-name-to-id.edn")))
(def app-name->id-path (or process.env.APTIBLE_APP_NAME_TO_ID_EDN_FILE
                           (str cwd "/examples/aptible/app-name-to-id.edn")))

;; The actual maps used to go from names to ids
(def stack-name->id
  "A hash-map where each key is the name of a stack, and each value is an integer
  used by aptible to represent it"
  (slurp-edn stack-name->id-path))
(def environment-name->id
  "A hash-map where each key is the name of an environment, and each value is an integer
  used by aptible to represent it"
  (slurp-edn environment-name->id-path))
(def app-name->id
  "A hash-map where each key is the name of an app, and each value is an integer
  used by aptible to represent it"
  (slurp-edn app-name->id-path))

(def security-scan-tuples
  "A vector of vectors, each of which specifies a [stack-name environment-name app-name].

  Each named entity must be present in the <entity>-name->id maps!

  These tuples are used by `examples.aptible/produce-security-scan-actions` to navigatate
  to the \"Security Scan\" tab to run the scan and capture results"
  (slurp-edn (str cwd "/examples/aptible/security-scan-tuples.edn")))

(defn produce-login-actions
  ([]
   (produce-login-actions {:email              env-email
                           :password           env-password
                           :multi-factor-token env-multi-factor-token}))
  ([{:keys [email password multi-factor-token]
     :or {email              env-email
          password           env-password
          multi-factor-token env-multi-factor-token}}]
   (let [email-selector              "input[type='email']"
         password-selector           "//input[@name='password' and @type='password']"
         multi-factor-token-selector "//input[@name='otp-token' and @type='text']"

         enclave-selector ".enclave-nav"]
     [[:wait-until-located email-selector]
      [:wait-until-visible email-selector]
      [:input-text email-selector email {:enter? false}]

      [:wait-until-located password-selector {:by :xpath}]
      [:wait-until-visible password-selector {:by :xpath}]
      [:input-text password-selector password {:enter? true :by :xpath}]

      [:wait-until-located multi-factor-token-selector {:by :xpath}]
      [:wait-until-visible multi-factor-token-selector {:by :xpath}]
      [:input-text multi-factor-token-selector multi-factor-token {:enter? true :by :xpath}]

      [:wait-until-located enclave-selector]
      [:click enclave-selector]])))

(defn produce-security-scan-actions
  [stack environment app]
  (let [stack-selector            (str "a[href='/stack/" (stack-name->id stack) "']")
        environment-selector      (str "a[href='/accounts/" (environment-name->id environment "']"))
        app-selector              (str "a[href='/apps/" (app-name->id app) "']")
        security-scan-selector    (str "a[href='/apps/" (app-name->id app) "/security-scan']")
        table-selector            "//table/tbody"
        table-or-success-selector "//table/tbody|//*[contains(@class,'alert-success')]"
        enclave-selector          ".enclave-nav"]
    [[:wait-until-located stack-selector]
     [:wait-until-visible stack-selector]
     [:click stack-selector]

     [:wait-until-located environment-selector {:timeout 5000}]
     [:wait-until-visible environment-selector]
     [:click environment-selector]

     [:wait-until-located app-selector {:timeout 5000}]
     [:wait-until-visible app-selector]
     [:click app-selector]

     [:wait-until-located security-scan-selector {:timeout 5000}]
     [:wait-until-visible security-scan-selector]
     [:click security-scan-selector]

     ;; Scrape the report from the page...
     ;; Inspired, at least in spirit, by
     ;; https://www.techbeamers.com/handling-html-tables-selenium-webdriver/
     [:wait-until-located table-or-success-selector {:by :xpath}]
     [:wait-until-visible table-or-success-selector {:by :xpath}]
     #(.executeScript poe/driver "document.getElementsByClassName('alert-success').length > 0;")
     (fn [no-vulnerabilities?]
       (if no-vulnerabilities?
         (js/Promise.resolve (clj->js []))
         (.then (.findElement poe/driver
                              (poe/by* :xpath table-selector))
                (fn [tbody]
                  (.then (.findElements tbody
                                        (poe/by* :css "tr"))
                         (fn [rows]
                           (let [promise-row-list
                                 (map-indexed
                                  (fn [idx row]
                                    (.then
                                     (.findElements (get (js->clj rows) idx)
                                                    (poe/by* :css "td"))
                                     (fn [cols]
                                       (let [promise-col-list
                                             (map-indexed
                                              (fn [col-idx col]
                                                (.getText (get (js->clj cols) col-idx)))
                                              cols)

                                             promise-col-array (clj->js promise-col-list)]
                                         (js/Promise.all promise-col-array)))))
                                  rows)

                                 promise-row-array (clj->js promise-row-list)]
                             (js/Promise.all promise-row-array))))))))
     (fn [rows]
       (let [filename  (str "aptible-security-scan-" stack "-" environment "-" app ".csv")
             directory (or env-output-dir cwd)
             file      (str directory "/" filename)]
         (js/console.log (str "Exporting table data to: " file))
         (spit file (csv-stringify rows))))
     [:wait-until-located enclave-selector]
     [:click enclave-selector]]))

(defn -main
  "Use config files to identify aptible apps, run the \"Security Scan\" on them,
  and capture the results. All done through selenium webdriver via the aptible web ui."
  [& _]
  (let [url "https://dashboard.aptible.com/login"]
    (poe/run
      {:url   url
       :quit? false}
      (reduce (fn [acc cur] (into acc (apply produce-security-scan-actions cur)))
              (produce-login-actions)
              security-scan-tuples))))

(comment
  (require 'poe.core
           'examples.aptible)

  ;; Aptible will reject requests if you log in too many times.
  ;; For this reason, if you are working on a solution, it is best to user a repl, login,
  ;; and continue to work from an existing session rather than to continue making new ones.

  (defn login-and-keep-open
    [mfa-token]
    (let [p (.get poe.core.driver "https://dashboard.aptible.com/login")
          login-actions (examples.aptible/produce-login-actions {:multi-factor-token mfa-token})]
      (poe.core/run {:promise p :quit? false} login-actions)))

  (login-and-keep-open "123456")

  (defn run-scan
    [stack env app]
    (poe.core/run {:promise (js/Promise.resolve nil) :quit? false}
      (examples.aptible/produce-security-scan-actions stack env app)))

  (run-scan "fake-stack" "fake-environment" "fake-app")

  )
