(ns ^{:doc "Aero integration"} skip.aero
  (:require
   [aero.core :as aero]
   [skip.core :refer [ISkip stale? refresh!]]
   [skip.notify :refer [INotify notify IWatchable watch unwatch new-watchers]]))

(defrecord AeroEdnFile [file-proxy profile *data *error watchers]
  ISkip
  (stale? [_] (stale? file-proxy))
  (refresh! [_]
    (reset! *data (aero/read-config (:file file-proxy) {:profile profile})))
  (error? [_] @*error)

  clojure.lang.IDeref
  (deref [_] @*data)

  INotify
  (notify [this _]
    (refresh! this)
    (notify watchers this))

  IWatchable
  (watch [_ watcher]
    (watch watchers watcher))
  (unwatch [_ watcher]
    (unwatch watchers watcher)))

(defn new-aero-edn-file [file-proxy profile]
  (let [data (aero/read-config (:file file-proxy) {:profile profile})
        edn-file (map->AeroEdnFile
                  {:file-proxy file-proxy
                   :profile profile
                   :*data (atom data)
                   :*error (atom nil)
                   :watchers (new-watchers)})]
    (watch file-proxy edn-file)
    edn-file))
