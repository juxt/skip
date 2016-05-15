(ns skip.notify)

(defprotocol INotify
  (notify [_ ev] "Callback when file changes"))

(extend-protocol INotify
  clojure.lang.Fn
  (notify [f ev] (f ev)))

(defprotocol IWatchable
  (watch [_ watcher] "Register an observer")
  (unwatch [_ watcher] "Unregister an observer"))

(defrecord Watchers [atm]
  clojure.lang.IDeref
  INotify
  (notify [this ev]
    (doseq [w @atm]
      (notify w ev)))
  IWatchable
  (watch [_ watcher]
    (assert watcher)
    (swap! atm conj watcher))
  (unwatch [_ watcher]
    (assert watcher)
    (swap! atm disj watcher)))

(prefer-method print-method clojure.lang.IRecord clojure.lang.IDeref)
(prefer-method print-method clojure.lang.IPersistentMap clojure.lang.IDeref)

(defmethod clojure.core/print-method Watchers
  [watchers ^java.io.Writer writer]
  (.write writer "#<Watchers>"))

(defmethod clojure.core/print-dup Watchers
  [watchers ^java.io.Writer writer]
  (.write writer "#<Watchers>"))

(defn new-watchers []
  (->Watchers (atom #{})))
