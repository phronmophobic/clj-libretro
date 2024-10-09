(ns com.phronemophobic.clj-libretro.skia
  (:require [membrane.skia :as skia]
            [membrane.ui :as ui]
            [clojure.string :as str]
            [com.phronemophobic.clj-libretro.ui :as retro-ui]
            [com.phronemophobic.clj-libretro.api :as retro]
            [clojure.java.io :as io])
  (:import com.sun.jna.Memory
           java.io.ByteArrayOutputStream))

(def ^:private pixmap @#'skia/pixmap)

(defn ^:private run-with-close-handler [view-fn opts close-handler]
  (skia/run view-fn
    (merge
     {:handlers {:window-close
                 (fn [window window-handle]
                   (close-handler))}}
     opts)))


(defn ^:private save-game
  "Serialize the current global game state to the file path `fname`."
  [iretro fname]
  (let [size (retro/retro_serialize_size iretro)
        mem (Memory. size)]
    (retro/retro_serialize iretro mem size)
    (let [buf (byte-array size)]
      (.read mem 0 buf 0 size)
      (with-open [os (io/output-stream fname)]
        (io/copy buf
                 os)))
    ))

(defn ^:private load-save
  "Read the serialized game state from the file path `fname` and overwrite the current global game state."
  [iretro fname]
  (let [baos (ByteArrayOutputStream.)]
    (with-open [is (io/input-stream fname)]
      (io/copy is baos))
    (let [buf (.toByteArray baos)]
      (retro/retro_unserialize iretro buf (alength buf)))))

(def RETRO_MEMORY_SYSTEM_RAM  2)
(def ram-size 0x800)
(defn ^:private get-memory
  "Return the current console RAM.

  Note: this is different than the game state."
  [iretro]
  (let [mem (retro/retro_get_memory_data iretro RETRO_MEMORY_SYSTEM_RAM)
        buf (byte-array ram-size)]
    (.read mem 0 buf 0 ram-size)
    buf))

(defn hud [iretro pm]
  (let [mem (get-memory iretro)]
    (ui/vertical-layout
     (ui/horizontal-layout
      (ui/button "save"
                 (fn []
                   (save-game iretro "ui.save")
                   nil))
      (ui/button "load"
                 (fn []
                   (load-save iretro "ui.save")
                   nil)))
     (ui/label
      (clojure.string/join
       (map str
            (reverse
             [(nth mem 96)
              (nth mem 97)
              (nth mem 98)
              (nth mem 99)
              (nth mem 100)
              (nth mem 101)
              ]))))
     pm)))

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
    :hud #(hud iretro %)
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

