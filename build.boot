(def project 'skip)

(require '[clojure.java.shell :as sh])

(defn next-version [version]
  (when version
    (let [[a b] (next (re-matches #"(.*?)([\d]+)" version))]
      (when (and a b)
        (str a (inc (Long/parseLong b)))))))

(defn deduce-version-from-git
  "Avoid another decade of pointless, unnecessary and error-prone
  fiddling with version labels in source code."
  []
  (let [[version commits hash dirty?]
        (next (re-matches #"(.*?)-(.*?)-(.*?)(-dirty)?\n"
                          (:out (sh/sh "git" "describe" "--dirty" "--long" "--tags" "--match" "[0-9].*"))))]
    (if (or dirty? (pos? (Long/parseLong commits)))
      (str (next-version version) "-SNAPSHOT")
      version)))

(deftask show-version "Show version" []
  (println (deduce-version-from-git)))

(def version (deduce-version-from-git))

(set-env! :resource-paths #{"src"}
          :source-paths   #{"test"}
          :dependencies   '[[org.clojure/clojure "RELEASE"]
                            [adzerk/boot-test "RELEASE" :scope "test"]])

(task-options!
 pom {:project     project
      :version     version
      :description "Skippy McSkipface - A general Clojure dependency tracker"
      :url         "https://github.com/juxt/skip"
      :scm         {:url "https://github.com/yourname/skip"}
      :license     {"The MIT License"
                    "http://opensource.org/licenses/MIT"}})

(deftask build
  "Build and install the project locally."
  []
  (comp (pom)
        (jar)
        (install)))

(declare repo-map)
(deftask deploy
  "Deploy the library. You need to add a repo-map function in your profile.boot that returns the url and credentials as a map.

For example:

{:url \"https://clojars.org/repo/\"
 :username \"billy\"
 :password \"thefish\"}"
  []
  (comp (pom)
        (jar)
        (target)
        (push :repo-map (repo-map "clojars")
              :file (format "target/%s-%s.jar" project version)
              ;;:gpg-sign (not (.endsWith +version+ "-SNAPSHOT"))
              )))

(require '[adzerk.boot-test :refer [test]])
