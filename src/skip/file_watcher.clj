(ns skip.file-watcher
  (:require
   [clojure.tools.logging :refer :all]
   [com.stuartsierra.component :refer [Lifecycle]]
   [skip.notify :refer [notify]]))

(defn register! [watcher file-proxy]
  (let [filepath (.toAbsolutePath (.toPath (:file file-proxy)))]
    (let [dirpath (.getParent filepath)]
      (locking (:dirpaths watcher)
        (when-not (contains? @(:dirpaths watcher) dirpath)
          (let [watch-key
                (.register dirpath
                           (:watch-service watcher)
                           (into-array
                            (type java.nio.file.StandardWatchEventKinds/ENTRY_MODIFY)
                            [java.nio.file.StandardWatchEventKinds/ENTRY_MODIFY]))]
            (swap! (:dirpaths watcher) conj dirpath)
            (swap! (:dirpaths-by-watchkey watcher) conj [watch-key dirpath])
            watch-key)))
      (swap! (:fileproxies-by-filepath watcher) conj [(.toString filepath) file-proxy]))))

(defn temp-file? [file]
  (re-matches #"\.#.*" (.getName file)))

(defrecord FileWatcher [watch-service continue]
  Lifecycle
  (start [component]
    (let [watch-service (.newWatchService (java.nio.file.FileSystems/getDefault))
          dirpaths (atom #{})
          dirpaths-by-watchkey (atom {})
          fileproxies-by-filepath (atom {})
          continue (atom true)
          t (.start
             (Thread.
              (fn []
                (try
                  (while @continue
                    (let [watch-key (.take watch-service)]
                      (try
                        (let [events (.pollEvents watch-key)]
                          (if-let [dirpath (get @dirpaths-by-watchkey watch-key)]
                            (doseq [ev events]
                              (let [filepath (.resolve dirpath (.context ev))]
                                (when-not (temp-file? (.toFile filepath))
                                  (when-let [file-proxy (get @fileproxies-by-filepath (.toString filepath))]
                                    (notify file-proxy :modify)))))
                            (warnf "No dirpath registered for watch-key: %s" watch-key)))
                        (catch Exception e
                          (errorf e "Exception")))
                      (let [valid (.reset watch-key)]
                        #_(if-not valid (swap! watch-keys dissoc watch-key)))))
                  (catch java.nio.file.ClosedWatchServiceException e
                    (infof "Watch service closed, giving up on watcher thread")
                    )))))]
      (assoc component
             :watch-service watch-service
             :dirpaths dirpaths
             :dirpaths-by-watchkey dirpaths-by-watchkey
             :fileproxies-by-filepath fileproxies-by-filepath
             :continue continue)))
  (stop [component]
    (when continue
      (reset! continue false))
    (when watch-service
      (.close watch-service))
    (dissoc component :watch-service :continue)))

(defn new-file-watcher []
  (map->FileWatcher {}))
