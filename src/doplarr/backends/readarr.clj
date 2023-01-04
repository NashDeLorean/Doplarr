(ns doplarr.backends.readarr
  (:require
   [clojure.core.async :as a]
   [doplarr.backends.readarr.impl :as impl]
   [doplarr.state :as state]
   [doplarr.utils :as utils]
   [fmnoise.flow :refer [then]]
   [taoensso.timbre :refer [warn]]))

(defn search [term _]
  (utils/request-and-process-body
   impl/GET
   #(map utils/process-search-result %)
   "/book/lookup"
   {:query-params {:term term}}))

(defn additional-options [result _]
  (a/go
    (let [language-profiles (a/<! (impl/language-profiles))
          rootfolders (a/<! (impl/rootfolders))
          details (a/<! (impl/get-from-id (:goodreads-id result)))
          {:keys [readarr/language-profile
                  readarr/rootfolder]} @state/config
          default-language-id (utils/id-from-name language-profiles language-profile)
          default-root-folder (utils/id-from-name rootfolders rootfolder)]
      (when (and language-profile (nil? default-language-id))
        (warn "Default language profile in config doesn't exist in backend, check spelling"))
      (when (and rootfolder (nil? default-root-folder))
        (warn "Default root folder in config doesn't exist in backend, check spelling"))
      {:language-profile-id (cond
                              language-profile default-language-id
                              (= 1 (count language-profiles)) (:id (first language-profiles))
                              :else language-profiles)
       :rootfolder-id (cond
                        default-root-folder default-root-folder
                        (= 1 (count rootfolders)) (:id (first rootfolders))
                        :else rootfolders)})))

(defn request-embed [{:keys [title goodreads-id rootfolder-id]} _]
  (a/go
    (let [rootfolders (a/<! (impl/rootfolders))
          details (a/<! (impl/get-from-id goodreads-id))]
      {:title title
       :overview (:overview details)
       :poster (:remote-poster details)
       :media-type :book
       :request-formats [""]
       :rootfolder (utils/name-from-id rootfolders rootfolder-id)})))

(defn request [payload _]
  (a/go
    (let [status (impl/status (a/<! (impl/get-from-id (:goodreads-id payload))))
          rfs (a/<! (impl/rootfolders))
          payload (assoc payload :root-folder-path (utils/name-from-id rfs (:rootfolder-id payload)))]
      (if status
        status
        (->> (a/<! (impl/POST "/book" {:form-params (utils/to-camel (impl/request-payload payload))
                                        :content-type :json}))
             (then (constantly nil)))))))