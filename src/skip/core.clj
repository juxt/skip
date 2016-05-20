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
  (stale? [_] "Is this thing stale, needing a refresh? If so, tell me why. Otherwise return nil.")
  (update! [_] "This is stale. Update this thing such that a stale? would be false. Don't do anything with dependencies. If you do anything, return truthy - because of update! will be the last thing called by refresh! and needs to return a value to indicate something changed.")
  (refresh! [_] "Call refresh! on all my dependencies. If anything was done, update! myself and return truthy, else return false."))

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
    (let [stale? (keep stale? (dependencies-seq dependencies))]
      (when (not-empty stale?) stale?)))
  (update! [this] nil)
  (refresh! [this]
    (some refresh! dependencies))

  INotify
  (notify [this dependency]
    (update! this)
    (notify watchers this))

  IWatchable
  (watch [this watcher]
    (watch watchers watcher))
  (unwatch [this watcher]
    (unwatch watchers watcher))

  clojure.lang.IDeref
  (deref [this]
    (refresh! this)
    (mapv deref dependencies))

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

(defrecord FileProxy [file last-modified watchers]
  ISkip
  (stale? [this]
    (when (< @last-modified (.lastModified file))
      this))

  (update! [this]
    (reset! last-modified (.lastModified file)))

  (refresh! [this]
    (when (stale? this)
      (update! this)))

  INotify
  (notify [this _]
    (update! this)
    (notify watchers this))

  IWatchable
  (watch [this watcher]
    (watch watchers watcher))
  (unwatch [this watcher]
    (unwatch watchers watcher))

  clojure.lang.IDeref
  (deref [this]
    (refresh! this)
    {:file file
     :last-modified @last-modified})

  IExplain
  (explain [this]
    (let [now (System/currentTimeMillis)
          actual (.lastModified file)]
      {:time-since-loaded (- now @last-modified)
       :time-since-modified (- now actual)
       :message (format "%s was modified on %s since it was last loaded on %s"
                        file
                        (new java.util.Date actual)
                        (new java.util.Date @last-modified))})))

(defn new-file-proxy [file & [watcher]]
  (let [file (io/file file)
        fp (->FileProxy file (atom 0) (new-watchers))]
    (when watcher (file-watcher/register! watcher fp))
    fp))
