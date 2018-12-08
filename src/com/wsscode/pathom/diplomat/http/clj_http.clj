(ns com.wsscode.pathom.diplomat.http.clj-http
  (:require [clj-http.client :as client]
            [com.wsscode.pathom.diplomat.http :as http]
            [clojure.spec.alpha :as s]
            [com.wsscode.common.async-clj :refer [go-catch go-promise]]
            [clojure.core.async :as async]))

;; TODO: ::http/debug?
(defn build-request-map
  [{::http/keys [url content-type accept as body headers form-params] :as req}]
  (let [q? (partial contains? req)]
    (cond-> {:url    url
             :method (keyword (http/request-method req))}
            (q? ::http/headers) (assoc :headers headers)
            (q? ::http/content-type) (assoc :content-type (keyword (name content-type)))
            (q? ::http/accept) (assoc :accept (http/encode-type->header accept))
            (q? ::http/as) (assoc :as (keyword (name as)))
            (q? ::http/form-params) (assoc :form-params form-params)
            (q? ::http/body) (assoc :body body))))

(defn build-response-map
  [{:keys [status body headers]
    :as   response}]
  (with-meta {::http/status  status
              ::http/body    body
              ::http/headers headers}
             response))

(defn request [req]
  (s/assert ::http/request req)
  (-> req
      build-request-map
      client/request
      build-response-map))

(defn request-async
  [req]
  (s/assert ::http/request req)
  (go-promise
    (let [chan (async/promise-chan)
          respond (fn request-async-respond [response]
                    (go-catch (async/>! chan (build-response-map response))))
          raise (fn request-async-raise [ex]
                  (async/>!! chan ex))]
      (-> req
          build-request-map
          (assoc :async? true)
          (client/request respond raise))
      (async/<! chan))))

(s/fdef request
  :args (s/cat :request ::http/request)
  :ret ::http/response)
