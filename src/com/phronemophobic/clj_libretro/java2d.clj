(ns com.phronemophobic.clj-libretro.java2d
  (:require [membrane.java2d :as java2d]
            [membrane.ui :as ui]
            [clojure.string :as str]
            [com.phronemophobic.clj-libretro.ui :as retro-ui])
  (:import java.awt.image.BufferedImage
           java.awt.event.WindowEvent))


(defmacro ^:private with-tile [[tile-bind img] & body]
  `(let [img# ~img
         ~tile-bind (.getWritableTile img# 0 0)]
     (try
       ~@body
       (finally
         (.releaseWritableTile img# 0 0)))))

(defn ^:private render-frame
  "Writes an AVFrame into a BufferedImage. Assumes :byte-bgr image format."
  [previous-img data width height pitch]
  (let [img (or (:image-path previous-img)
                (BufferedImage. width height BufferedImage/TYPE_USHORT_565_RGB))]
    (with-tile [wraster img]
      (doseq [y (range height)]
        (.setDataElements wraster 0 y width 1
                          (.getShortArray data (* pitch y) pitch))))
    (ui/image img)))

(defn ^:private run-with-close-handler
  [view-fn opts close-handler]
  (let [winfo (java2d/run view-fn opts)
        frame (::java2d/frame winfo)]
    (.addWindowListener frame
                        (reify java.awt.event.WindowListener
                          (^void windowActivated [this ^WindowEvent e])
                          (^void windowClosed [this ^WindowEvent e])
                          (^void windowClosing [this ^WindowEvent e]
                           (close-handler))
                          (^void windowDeactivated [this ^WindowEvent e])
                          (^void windowDeiconified [this ^WindowEvent e])
                          (^void windowIconified [this ^WindowEvent e])
                          (^void windowOpened [this ^WindowEvent e])))
    (.toFront frame)

    winfo))

(defn ^:private ->repaint! [winfo]
  (::java2d/repaint winfo))

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


