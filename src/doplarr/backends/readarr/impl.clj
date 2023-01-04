(ns doplarr.backends.readarr.impl
  (:require
   [clojure.core.async :as a]
   [doplarr.state :as state]
   [doplarr.utils :as utils]
   [fmnoise.flow :as flow :refer [then]]))

(def base-url (delay (str (:readarr/url @state/config) "/api/v1")))
(def api-key  (delay (:readarr/api @state/config)))

(defn GET [endpoint & [params]]
  (utils/http-request :get (str @base-url endpoint) @api-key params))

(defn POST [endpoint & [params]]
  (utils/http-request :post (str @base-url endpoint) @api-key params))

(defn PUT [endpoint & [params]]
  (utils/http-request :put (str @base-url endpoint) @api-key params))

(defn get-from-id [goodreads-id]
  (utils/request-and-process-body
   GET
   (comp utils/from-camel first)
   "/book/lookup"
   {:query-params {:term (str "foreignBookId" goodreads-id)}}))

(defn language-profiles []
  (utils/request-and-process-body
   GET
   #(map utils/process-profile %)
   "/languageProfile"))

(defn rootfolders []
  (utils/request-and-process-body
   GET
   utils/process-rootfolders
   "/rootfolder"))

(defn execute-command [command & {:as opts}]
  (a/go
    (->> (a/<! (POST "/command" {:form-params (merge {:name command} opts)
                                 :content-type :json}))
         (then (constantly nil)))))

(defn search-book [goodreads-id]
  (a/go
    (->> (a/<! (execute-command "BookSearch" {:foreignBookId goodreads-id})))
    (then (constantly nil))))

(defn status [details]
  (cond
    (and (:has-file details)
         (:is-available details)
         (:monitored details)) :available
    (and (not (:has-file details))
         (:is-available details)
         (:monitored details)) :processing
    :else nil))

(defn request-payload [payload]
  (-> payload
      (select-keys [:title :goodreads-id :quality-profile-id :root-folder-path])
      (assoc :monitored true
             :add-options {:search-for-new-book true})))
