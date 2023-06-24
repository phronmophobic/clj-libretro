(ns com.phronemophobic.clj-libretro.skia
  (:require [membrane.skia :as skia]
            [membrane.ui :as ui]
            [clojure.string :as str]
            [com.phronemophobic.clj-libretro.ui :as retro-ui]))

(def ^:private pixmap @#'skia/pixmap)

(defn ^:private run-with-close-handler [view-fn opts close-handler]
  (skia/run view-fn
    (merge
     {:handlers {:window-close
                 (fn [window window-handle]
                   (close-handler))}}
     opts)))

(defn ^:private render-frame [pm data width height pitch]
  (let [len (* 2
               height
               pitch)
        color-type skia/kRGB_565_SkColorType
        alpha-type skia/kUnpremul_SkAlphaType
        pm (if pm
             (update pm :id inc)
             (pixmap 0 (byte-array len) width height color-type alpha-type pitch))
        
        dest (:buf pm)]
    (System/arraycopy (.getByteArray data 0 len)
                      0
                      dest
                      0
                      len)
    pm))

(defn ^:private ->repaint! [winfo]
  @#'skia/glfw-post-empty-event)

(defn play-game [iretro full-path]
  (retro-ui/play-game
   iretro
   full-path
   {:run-with-close-handler run-with-close-handler
    :render-frame render-frame
    :->repaint! ->repaint!}))

(defn -main [game-path]
  (let [iretro (if (str/ends-with? game-path ".nes")
                 (@(requiring-resolve 'com.phronemophobic.clj-libretro.api/load-core) "fceumm")
                 (@(requiring-resolve 'com.phronemophobic.clj-libretro.api/load-core) "snes9x"))]
    (play-game iretro game-path)
    (System/exit 0)))

(comment

  (play-game
   (com.phronemophobic.clj-libretro.api/load-core "fceumm")
   "Super Mario Bros. 2.nes")

  (play-game
   (com.phronemophobic.clj-libretro.api/load-core "fceumm")
   "testroms/tsone/neskit/2048.nes")

  (play-game
   (com.phronemophobic.clj-libretro.api/load-core "snes9x")
   "Super Mario World.smc")

  ,)

