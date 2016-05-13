;; Copyright © 2016, JUXT LTD.

(ns skip.core
  (:require
   [clojure.java.io :as io]))

(defprotocol Dependencies
  (add-dependency [_ dependency] "Add a dependency (immutable)")
  (add-dependency! [_ dependency] "Add a dependency (mutable)")
  (dependencies-seq [_] "Return the dependencies"))

(extend-protocol Dependencies

  clojure.lang.Atom
  (add-dependency! [atm dependency]
    (swap! atm add-dependency dependency))
  (dependencies-seq [atm] (dependencies-seq (deref atm)))

  clojure.lang.Seqable
  (dependencies-seq [this] (seq this))

  clojure.lang.PersistentVector
  (add-dependency [this dependency]
    (conj this dependency))
  (dependencies-seq [this] (seq this))

  clojure.lang.IPersistentCollection
  (add-dependency [this dependency]
    (conj this dependency)))

(defprotocol Expirable
  (fresh? [_] "A dependant is fresh if all its dependencies are fresh")
  (freshen [_] "Set to fresh"))

(defrecord NeverFresh []
  Expirable
  (fresh? [_] false))

(defrecord AlwaysFresh []
  Expirable
  (fresh? [_] true))

(defrecord Dependant [dependencies]
  Expirable
  (fresh? [this]
    (every? fresh? (dependencies-seq dependencies)))

  Dependencies ; proxying
  (add-dependency! [_ dependency]
    (add-dependency! dependencies dependency))
  (dependencies-seq [_]
    (dependencies-seq dependencies)))

;; A file can be fresh, then stale, then freshened

(defrecord FileProxy [file last-modified]
  Expirable
  (fresh? [this]
    (= (.lastModified file) last-modified))
  (freshen [this]
    (when-not (fresh? this)
      (->FileProxy file (.lastModified file)))))

(defn new-file-proxy [file]
  (let [file (io/file file)]
    (->FileProxy file (.lastModified file))))
