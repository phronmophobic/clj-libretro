(ns com.phronemophobic.clj-libretro.raw
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.java.io :as io]
            ;; [com.phronemophobic.clong.clang :as clong]
            [com.phronemophobic.clong.gen.jna :as gen]))


(defn ^:private write-edn [w obj]
  (binding [*print-length* nil
            *print-level* nil
            *print-dup* false
            *print-meta* false
            *print-readably* true

            ;; namespaced maps not part of edn spec
            *print-namespace-maps* false

            *out* w]
    (pr obj)))

(defn ^:private dump-api []
  (let [outf (io/file
              "resources"
              "com"
              "phronemophobic"
              "clj-libretro"
              "api.edn")]
    (.mkdirs (.getParentFile outf))
    (with-open [w (io/writer outf)]
      (write-edn w
                 ((requiring-resolve 'com.phronemophobic.clong.clang/easy-api)
                  "/Users/adrian/workspace/libretro-super/libretro-fceumm/src/drivers/libretro/libretro-common/include/libretro.h")
                 ))))

(def api
  #_((requiring-resolve 'com.phronemophobic.clong.clang/easy-api) "/Users/adrian/workspace/libretro-super/libretro-fceumm/src/drivers/libretro/libretro-common/include/libretro.h")
  
  (with-open [rdr (io/reader
                     (io/resource
                      "com/phronemophobic/clj-libretro/api.edn"))
                rdr (java.io.PushbackReader. rdr)]
      (edn/read rdr)))

(def lib (com.sun.jna.NativeLibrary/getInstance "retro"))

;; (def struct-prefix (gen/ns-struct-prefix *ns*))
;; (gen/def-enums (:enums api))
;; (gen/def-structs (:structs api) struct-prefix)

;; (defn ^:private def-fn*
;;   [struct-prefix f]
;;   (let [args (mapv (fn [arg]
;;                      (let [arg-name (:spelling arg)]
;;                        (symbol
;;                         (if (= arg-name "")
;;                           (str/replace (:type arg)
;;                                        #" "
;;                                        "_")
;;                           arg-name))))
;;                    (:args f))
;;         cfn-sym (with-meta (gensym "cfn") {:tag 'com.sun.jna.Function})
;;         fn-name (symbol (:symbol f))
;;         lib## (gensym "lib_")
;;         ]
;;     `(fn []
;;        (let [struct-prefix# ~struct-prefix
;;              ret-type# (gen/coffi-type->jna struct-prefix#
;;                                             ~(:function/ret f))
;;              coercers#
;;              (doall (map #(gen/coercer struct-prefix# %) ~(:function/args f)))]
;;          (defn ~fn-name
;;            ~(let [doc (:raw-comment f)]
;;               (str
;;                (-> f :ret :spelling) " " (:name f) "("
;;                (str/join ", "
;;                          (eduction
;;                           (map (fn [arg]
;;                                  (str (:type arg)
;;                                       " "
;;                                       (:spelling arg)))
;;                                (:args f))))
;;                ")"
;;                "\n"
;;                doc))
;;            [~'lib ~@args]
;;            (let [~cfn-sym (.getFunction ~(with-meta 'lib {:tag 'com.sun.jna.NativeLibrary})
;;                                         ~(name fn-name))
;;                  args# (map (fn [coerce# arg#]
;;                               (coerce# arg#))
;;                             coercers#
;;                             ~args)]
;;              (.invoke ~cfn-sym
;;                       ret-type# (to-array args#))))))))

;; (defmacro ^:private def-functions [functions struct-prefix]
;;   `(run! #((eval (def-fn* ~struct-prefix %))) ~functions))

;; (def-functions (:functions api) struct-prefix)

(gen/def-api lib api)
(gen/import-structs! api)

(let [struct-prefix (gen/ns-struct-prefix *ns*)]
  (defmacro import-structs! []
    `(gen/import-structs! api ~struct-prefix)))

(defn get-av-info []
  (let [s (retro_system_av_infoByReference.)]
    (retro_get_system_av_info s)
    (let [timing (.readField s "timing")
          geometry (.readField s "geometry")]
      {:timing {:fps (.readField timing "fps")
                :sample-rate (.readField timing "sample_rate")}
       :geometry {:base-width (.readField geometry "base_width")
                  :base-height (.readField geometry "base_height")
                  :max-width (.readField geometry "max_width")
                  :max-height (.readField geometry "max_height")
                  :aspect-ratio (.readField geometry "aspect_ratio")}})))
