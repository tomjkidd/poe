(ns examples.aptible
  "An example of how to use poe/run to perform some security scanning of aptible apps

  APTIBLE_EMAIL=fake@email.com APTIBLE_PASSWORD=p@ssW0rD APTIBLE_MULTI_FACTOR_TOKEN=123456 lumo --classpath src:examples -m examples.aptible
  "
  (:require [cljs.pprint :refer [pprint print-table]]
            [clojure.string :as str]
            [poe.core :as poe]
            [poe.file-export :as file-export]
            [poe.html.scrape :as html-scrape]
            [poe.util :as util]))

(def cwd (js/process.cwd))

(def slurp-edn util/slurp-edn)

(def env-email process.env.APTIBLE_EMAIL)
(def env-password process.env.APTIBLE_PASSWORD)
(def env-multi-factor-token process.env.APTIBLE_MULTI_FACTOR_TOKEN)
(def env-output-dir process.env.APTIBLE_OUTPUT_DIR)
(def security-scan-tuples-path (or process.env.APTIBLE_SECURITY_SCAN_TUPLE_EDN_FILE
                                   (str cwd "/examples/aptible/security-scan-tuples.edn")))
(def security-scan-tuples
  "A vector of vectors, each of which specifies a [stack-name environment-name
  app-name].

  Each named entity must be present in the result produced by
  determine-aptible-identifiers!

  These tuples are used by `examples.aptible/produce-security-scan-actions` to
  navigatate to the \"Security Scan\" tab to run the scan and capture results."
  (delay (slurp-edn security-scan-tuples-path)))

(def landing-page-selector
  "The selector to click to bring you back to the expected landing page"
  ".enclave-nav")

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
         multi-factor-token-selector "//input[@name='otp-token' and @type='text']"]
     [[:wait-until-located email-selector]
      [:wait-until-visible email-selector]
      [:input-text email-selector email {:enter? false}]

      [:wait-until-located password-selector {:by :xpath}]
      [:wait-until-visible password-selector {:by :xpath}]
      [:input-text password-selector password {:enter? true :by :xpath}]

      [:wait-until-located multi-factor-token-selector {:by :xpath}]
      [:wait-until-visible multi-factor-token-selector {:by :xpath}]
      [:input-text multi-factor-token-selector multi-factor-token {:enter? true :by :xpath}]

      [:wait-until-located landing-page-selector]
      [:click landing-page-selector]])))

(defn produce-security-scan-actions
  [aptible-identifiers stack environment app]
  (let [stack-selector         (str "a[href='/stack/" (get-in aptible-identifiers [:stack-name->id stack]) "']")
        environment-selector   (str "a[href='/accounts/" (get-in aptible-identifiers [:environment-name->id environment] "']"))
        app-selector           (str "a[href='/apps/" (get-in aptible-identifiers [:environment-name->app-name->app-id environment app]) "']")
        security-scan-selector (str "a[href='/apps/" (get-in aptible-identifiers [:environment-name->app-name->app-id environment app]) "/security-scan']")
        table-selector         "//table"
        scan-result-selector   "//table|//*[contains(@class,'alert-success')]|//*[contains(@class,'alert-danger')]"]
    [[:wait-until-located stack-selector {:timeout 10000}]
     [:wait-until-visible stack-selector]
     [:click stack-selector]

     [:wait-until-located environment-selector {:timeout 10000}]
     [:wait-until-visible environment-selector]
     [:click environment-selector]

     [:wait-until-located app-selector {:timeout 10000}]
     [:wait-until-visible app-selector]
     [:click app-selector]

     [:wait-until-located security-scan-selector {:timeout 5000}]
     [:wait-until-visible security-scan-selector]
     [:click security-scan-selector]

     ;; Provide some time for the scan to complete
     [:wait-until-located scan-result-selector {:timeout 60000
                                                :by      :xpath}]
     [:wait-until-visible scan-result-selector {:by :xpath}]
     #(.then (.executeScript poe/driver "return document.getElementsByClassName('alert-success').length > 0;")
             (fn [success?]
               (.then (.executeScript poe/driver "return document.getElementsByClassName('alert-danger').length > 0;")
                      (fn [unsupported?]
                        (cond
                          success?     :no-vulnerabilities
                          unsupported? :unsupported
                          :else        :found-vulnerabilities)))))
     (fn [status]
       (case status
         :no-vulnerabilities
         (do
           (println (str "No vulnerabilities found for app '" app "' in environment '" environment "'."))
           (js/Promise.resolve []))

         :unsupported
         (do
           (println (str "Unsupported app '" app "' in environment '" environment "'. See aptible for more details."))
           (js/Promise.resolve []))

         :found-vulnerabilities
         (html-scrape/scrape-table poe/driver {:selector        table-selector
                                               :by              :xpath
                                               :include-header? true})))
     (fn [rows]
       (let [filename  (str "aptible-security-scan."
                            stack "." environment "." app "."
                            (.toISOString (js/Date.)) ".csv")
             directory (or env-output-dir cwd)
             file      (str directory "/" filename)]
         (js/console.log (str "Exporting table data to: " file))
         (file-export/csv file rows)))
     [:wait-until-located landing-page-selector]
     [:click landing-page-selector]]))

(defn determine-aptible-identifiers
  "Returns a promise that will eventually know all stack/env/app ids, and
  provide lookups to get from names to the ids.

  Demonstrates how to scrape the aptible landing page to extract all of the ids
  to be able to use stack/env/app names instead of ids.

  The aptible UI renders elements with hrefs to ids, not the convenient names of
  stacks/environments/apps. For this reason, it is most simple to create a
  mapping of names to ids so that the selectors used in navigation are more
  reliable."
  []
  (let [stack-selector "//*[@class='nav stack-list']//a"
        by             :xpath]
    (-> (.findElements poe/driver (poe/by* by stack-selector))
        (.then (fn [stack-web-elements]
                 (reduce (fn [acc-promise e]
                           (.then acc-promise
                                  (fn [acc]
                                    (-> e
                                        (.getAttribute "href")
                                        (.then (fn [href]
                                                 (.then (.getText e)
                                                        (fn [name]
                                                          (conj acc [(str/lower-case name) href])))))))))
                         (js/Promise.resolve [])
                         stack-web-elements)))
        (.then (fn [name-href-tuples]
                 (reduce (fn [acc [name href]]
                           (cond
                             ;; accounts correspond to environments
                             (str/includes? href "/accounts/")
                             (-> acc
                                 (update :stack-name-environment-name-href-tuples conj [(:current-stack acc) name href])
                                 (assoc-in [:environment-name->id name] (last (str/split href #"/"))))

                             ;; Capture the stack, so that each following environment can be associated with it
                             (str/includes? href "/stack/")
                             (-> acc
                                 (assoc :current-stack name)
                                 (assoc-in [:stack-name->id name] (last (str/split href #"/"))))))
                         {:current-stack                           nil
                          :stack-name-environment-name-href-tuples []
                          :app-scan-tuples                         []

                          :stack-name->id                     {}
                          :environment-name->id               {}
                          :environment-name->app-name->app-id {}}
                         ;; There is an item with a nil href for creating a new environment
                         (filter #(some? (second %))
                                 name-href-tuples))))
        (.then (fn [result]
                 (.then (.executeScript poe/driver "return window.location.hostname;")
                        (fn [hostname]
                          (assoc result :hostname hostname)))))
        (.then (fn [{:keys [stack-name-environment-name-href-tuples hostname] :as result}]
                 ;; Navigate to each environment to scrape the apps that are there
                 ;; Note, reduce is used to make this happen sequentially, since js/Promise.all doesn't ensure that!
                 (reduce
                  (fn [result-acc-promise [stack-name env-name href]]
                    (let [relative-href (second (str/split href hostname))]
                      (.then result-acc-promise
                             (fn [result-acc]
                               (-> (.findElement poe/driver (poe/by* :xpath (str "//a[@href='" relative-href "']")))
                                   (.click)
                                   ;; Wait for page to load
                                   (.then (fn [_]
                                            (.wait poe.core/driver
                                                   (fn []
                                                     (-> (.getCurrentUrl poe/driver)
                                                         (.then #(str/includes? % href))))
                                                   5000)))
                                   (.then (fn [_]
                                            (.findElements poe/driver (poe/by* :xpath "//*[contains(@class, 'resource-row app')]"))))
                                   (.then (fn [app-elements]
                                            (reduce (fn [app-acc-promise app-element]
                                                      (.then app-acc-promise
                                                             (fn [app-acc]
                                                               (-> app-element
                                                                   (.getAttribute "href")
                                                                   (.then (fn [href]
                                                                            (-> (.findElement app-element (poe/by* :css ".resource-row__title"))
                                                                                (.then #(.getText %))
                                                                                (.then (fn [name]
                                                                                         ;; href in the form https://dashboard.aptible.com/apps/123456, we just want the id
                                                                                         (conj app-acc [name (last (str/split href #"/"))]))))))))))
                                                    (js/Promise.resolve [])
                                                    app-elements)))
                                   (.then (fn [app-name-id-tuples]
                                            {:app-map   (into {} app-name-id-tuples)
                                             :app-names (mapv first app-name-id-tuples)}))
                                   (.then (fn [{:keys [app-map app-names]}]
                                            (-> result-acc
                                                (assoc-in [:environment-name->app-name->app-id env-name] app-map)
                                                (update :app-scan-tuples #(into % (mapv (fn [app-name]
                                                                                          [stack-name env-name app-name])
                                                                                        app-names)))))))))))
                  (js/Promise.resolve result)
                  stack-name-environment-name-href-tuples))))))

(defn validate-edn-configuration
  "Makes sure that for every stack/env/app in security-scan-tuples, there are
  mappings"
  [aptible-identifiers security-scan-tuples]
  (reduce (fn [acc [stack env app]]
            (cond-> acc
              (nil? (get-in aptible-identifiers [:stack-name->id stack]))
              (update :errors conj {:type :stack-name-mapping
                                    :msg  (str "Stack '" stack "' doesn't have an entry in the stack-name->id map.")})

              (nil? (get-in aptible-identifiers [:environment-name->id env]))
              (update :errors conj {:type :environment-name-mapping
                                    :msg  (str "Environment '" env "' doesn't have an entry in the environment-name->id map.")})

              (nil? (get-in aptible-identifiers [:environment-name->app-name->app-id env app]))
              (update :errors conj {:type :app-name-mapping
                                    :msg  (str "App '" app "' doesn't have an entry in the environment-name->app-name->id map.")})))
          {:errors []}
          security-scan-tuples))

(defn -main
  "Use web ui to identify aptible apps, run the \"Security Scan\" on the ones
  identified in config file, and capture the results. All done through selenium
  webdriver via the aptible web ui."
  [& args]
  (let [url            "https://dashboard.aptible.com/login"
        scan-all-apps? (some #(= "--scan-all-apps" %) args)]
    (.then (poe/run
             {:url   url
              :quit? false}
             (into (produce-login-actions)
                   [(fn [_]
                      (-> (determine-aptible-identifiers)
                          (.then (fn [aptible-identifiers]
                                   (let [app-scan-tuples   (if scan-all-apps?
                                                             (:app-scan-tuples aptible-identifiers)
                                                             @security-scan-tuples)
                                         validation-result (validate-edn-configuration aptible-identifiers app-scan-tuples)]
                                     {:aptible-identifiers aptible-identifiers
                                      :validation-result   validation-result
                                      :app-scan-tuples     app-scan-tuples})))
                          (.then (fn [{:keys [validation-result] :as context}]
                                   (let [{:keys [errors]} validation-result]
                                     (if (seq errors)
                                       (do
                                         (println "Errors detected:")
                                         (doseq [error errors]
                                           (pprint error))
                                         (-> (.close poe/driver)
                                             (.then #(.quit poe/driver))
                                             (.then #(.exit js/process 1))))
                                       context))))))]))
           (fn [{:keys [aptible-identifiers app-scan-tuples]}]
             (println "Attempting to scan the following:")
             (print-table
              [:stack :environment :app]
              (mapv (fn [[s e a]]
                      {:stack s :environment e :app a})
                    app-scan-tuples))
             (poe/run
               {:promise (js/Promise.resolve nil)
                :quit?   true}
               (reduce (fn [acc cur] (into acc (apply produce-security-scan-actions
                                                      aptible-identifiers
                                                      cur)))
                       [[:click landing-page-selector]]
                       app-scan-tuples))))))

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
