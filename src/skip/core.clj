;; Copyright © 2016, JUXT LTD.

(ns skip.core
  (:require
   [com.stuartsierra.component :refer [Lifecycle]]
   [clojure.tools.logging :refer :all]
   [skip.notify :refer [INotify notify IWatchable watch unwatch new-watchers]]
   [skip.file-watcher :as file-watcher]
   [clojure.java.io :as io]))

(defprotocol IDependencies
  (add-dependency [_ dependency] "Add a dependency (immutable)")
  (add-dependency! [_ dependency] "Add a dependency (mutable)")
  (dependencies-seq [_] "Return the dependencies"))

(extend-protocol IDependencies

  clojure.lang.Atom
  (add-dependency! [atm dependency]
    (swap! atm add-dependency dependency))
  (dependencies-seq [atm] (dependencies-seq (deref atm)))

  clojure.lang.Seqable
  (dependencies-seq [this] (seq this))

  clojure.lang.LazySeq
  (dependencies-seq [this] (seq this))

  clojure.lang.PersistentVector
  (add-dependency [this dependency]
    (conj this dependency))
  (dependencies-seq [this] (seq this))

  clojure.lang.IPersistentCollection
  (add-dependency [this dependency]
    (conj this dependency)))

(defprotocol ISkip
  (error? [_] "Is the instance in an error state?")
  (stale? [_] "Is this thing stale, needing a refresh? If so, tell me why. Otherwise return nil.")
  (refresh! [_] "Refresh this thing so it isn't stale."))

(defn ensure-fresh! [x]
  (if-let [error (error? x)]
    error
    (when (stale? x)
      (refresh! x))))

(defprotocol IExplain
  (explain [_] "What evidence is there that this thing is stale?"))

(defrecord NeverFresh []
  ISkip
  (stale? [_] true))

(defrecord AlwaysFresh []
  ISkip
  (stale? [_] nil))

(defrecord Dependant [dependencies watchers]
  ISkip
  (stale? [this]
    ;; TODO: First check if each dependency is in an error state
    (let [stale? (keep stale? (dependencies-seq dependencies))]
      (when (not-empty stale?) stale?)))
  (refresh! [this] nil)
  (error? [_] nil)

  INotify
  (notify [this dependency]
    (refresh! this)
    (notify watchers this))

  IWatchable
  (watch [this watcher]
    (watch watchers watcher))
  (unwatch [this watcher]
    (unwatch watchers watcher))

  clojure.lang.IDeref
  (deref [_] (mapv deref dependencies))

  IDependencies ; proxying, possible deprecated
  (add-dependency! [_ dependency]
    (add-dependency! dependencies dependency))
  (dependencies-seq [_]
    (dependencies-seq dependencies)))

(defn new-dependant
  "Create a dependant that aggregates across numerous dependencies"
  [dependencies]
  (assert vector? dependencies)
  (let [dependant (->Dependant dependencies (new-watchers))]
    (doseq [d dependencies]
      (watch d dependant))
    dependant))

;; A file can be fresh, then stale, then freshened

(defrecord FileProxy [file *last-modified watchers]
  ISkip
  (stale? [this]
    (when (< @*last-modified (.lastModified file))
      this))
  (refresh! [this]
    (reset! *last-modified (.lastModified file)))
  (error? [this] nil)

  INotify
  (notify [this _]
    ;; By default, the notification causes the watchers to be informed
    ;; of a new instance of this record where work has been performed
    ;; to maintain freshness.
    #_(infof "FileProxy has detected a change in %s, should inform the %s watchers" file watchers)
    (refresh! this)
    (infof "FileProxy for %s, notifying %d watchers" file (count watchers))
    (notify watchers this))

  IWatchable
  (watch [this watcher]
    (watch watchers watcher))
  (unwatch [this watcher]
    (unwatch watchers watcher))

  clojure.lang.IDeref
  (deref [_]
    {:file file
     :last-modified @*last-modified})

  IExplain
  (explain [this]
    (let [now (System/currentTimeMillis)
          actual (.lastModified file)]
      {:time-since-loaded (- now @*last-modified)
       :time-since-modified (- now actual)
       :message (format "%s was modified on %s since it was last loaded on %s"
                        file
                        (new java.util.Date actual)
                        (new java.util.Date @*last-modified))})))

(defn new-file-proxy [file & [watcher]]
  (let [file (io/file file)
        fp (->FileProxy file (atom (.lastModified file)) (new-watchers))]
    (when watcher (file-watcher/register! watcher fp))
    fp))
