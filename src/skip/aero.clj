(ns ^{:doc "Aero integration"} skip.aero
  (:require
   [clojure.tools.logging :refer :all]
   [aero.core :as aero]
   [skip.core :refer [ISkip stale? update! refresh!]]
   [skip.notify :refer [INotify notify IWatchable watch unwatch new-watchers]]))

(defrecord AeroEdnFile [file-proxy profile data watchers]
  ISkip
  (stale? [_]
    (stale? file-proxy))

  (update! [_]
    (reset! data (aero/read-config (:file file-proxy) {:profile profile})))

  (refresh! [this]
    (when (refresh! file-proxy)
      (update! this)))

  clojure.lang.IDeref
  (deref [this]
    (refresh! this)
    @data)

  INotify
  (notify [this _]
    (update! this)
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
                   :data (atom data)
                   :watchers (new-watchers)})]
    (watch file-proxy edn-file)
    edn-file))
