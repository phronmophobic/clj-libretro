(ns com.phronemophobic.clj-libretro.skia
  (:require [membrane.skia :as skia]
            [membrane.ui :as ui]
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

(comment
  (play-game
   @(requiring-resolve 'com.phronemophobic.clj-libretro.nes/iretro)
   "Super Mario Bros. 2.nes")

  (play-game
   @(requiring-resolve 'com.phronemophobic.clj-libretro.snes/iretro)
   "Super Mario World.smc")
  (play-game "Super Mario Bros. (World).nes")
  ,)

