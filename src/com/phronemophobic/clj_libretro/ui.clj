(ns com.phronemophobic.clj-libretro.ui
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            clojure.math
            [com.phronemophobic.clj-libretro.constants :as c]
            [com.phronemophobic.clj-libretro.raw :as retro]
            [membrane.ui :as ui]
            [clojure.pprint :refer [pprint]]
            [clojure.edn :as edn]
            [com.phronemophobic.clong.gen.jna :as gen])
  
  (:import
   java.util.concurrent.LinkedBlockingQueue
   ;; java.awt.image.BufferedImage
   ;; javax.imageio.ImageIO
   java.io.ByteArrayOutputStream
   java.io.PushbackReader
   com.sun.jna.Memory
   com.sun.jna.Pointer
   com.sun.jna.ptr.PointerByReference
   com.sun.jna.ptr.ByteByReference
   com.sun.jna.ptr.LongByReference
   com.sun.jna.ptr.IntByReference
   com.sun.jna.Structure
   java.io.ByteArrayInputStream
   (javax.sound.sampled AudioFormat
                        AudioFormat$Encoding
                        AudioInputStream
                        AudioSystem
                        DataLine
                        DataLine$Info
                        Line
                        LineUnavailableException
                        SourceDataLine
                        UnsupportedAudioFileException
                        )))



(retro/import-structs!)

(defn write-string [struct field s]
  (let [bytes (.getBytes s "utf-8")
        mem (doto (Memory. (inc (alength bytes)))
                   (.write 0 bytes 0 (alength bytes))
                   (.setByte (alength bytes) 0))
        bbr (doto (ByteByReference.)
               (.setPointer mem))]
    (doto struct
      (.writeField field bbr))))

(defn get-string [struct field]
  (when-let [f (.readField struct field)]
    (-> f
        .getPointer
        (.getString 0))))

(defn set-environment [cmd data]
  (case (c/name cmd)
    :RETRO_ENVIRONMENT_SET_CONTENT_INFO_OVERRIDE 1

    :RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE
    (do
      (.setInt data 0 (bit-or 1 2))
      0)

    :RETRO_ENVIRONMENT_GET_VARIABLE 0
    #_(do
      (if data
        (let [retro-variable (Structure/newInstance retro_variableByReference data)]
          (case (get-string retro-variable "key")
            "fceumm_sndvolume"
            (do (write-string retro-variable "value" "5")
                1)

            "fceumm_sndquality"
            (do (write-string retro-variable "value" "Very High")
                1)

            "fceumm_sndlowpass"
            (do ;; (write-string retro-variable "value" "enabled")
              1)

            "fceumm_sndstereodelay"
            (do #_(write-string retro-variable "value" "5")
                 0)

            ;; else
            0))
        0))

    :RETRO_ENVIRONMENT_SET_VARIABLE 0

    :RETRO_ENVIRONMENT_GET_LANGUAGE 0

    :RETRO_ENVIRONMENT_GET_GAME_INFO_EXT 0

    ;; else
    0))

;; Render a frame. Pixel format is 15-bit 0RGB1555 native endian
;; unless changed (see RETRO_ENVIRONMENT_SET_PIXEL_FORMAT).
;;
;; Width and height specify dimensions of buffer.
;; Pitch specifices length in bytes between two lines in buffer.
;;
;; For performance reasons, it is highly recommended to have a frame
;; that is packed in memory, i.e. pitch == width * byte_per_pixel.
;; Certain graphic APIs, such as OpenGL ES, do not like textures
;; that are not packed in memory.
;; (def video-pixmap (atom nil))

(defn video-refresh-callback
  [render-frame video-pixmap*]
  (fn [data width height pitch]
    (swap! video-pixmap*
           render-frame
           data width height pitch)))


;; /* Renders a single audio frame. Should only be used if implementation
;;  * generates a single sample at a time.
;;  * Format is signed 16-bit native endian.
;;  */
(defn audio-sample [left right]
  ;; (prn "sample audio" left right)
  
  )

(defn play-sound [sample-rate]
  (let [
        ;; sample-rate
        ;; 48000 ;; nes
        ;; 32040 ;; snes
        
        sample-size-in-bits 16
        channels 2
        ;; this is an educated guess
        ;; channels * sample-size-in-bits/byte-size
        frame-size (* channels (/ sample-size-in-bits 8))

        ;; frame-rate 48000 ;;44100
        frame-rate sample-rate
        big-endian? false
        audio-format (AudioFormat. AudioFormat$Encoding/PCM_SIGNED
                                   sample-rate
                                   sample-size-in-bits
                                   channels
                                   frame-size
                                   frame-rate
                                   big-endian?)
        info (DataLine$Info. SourceDataLine
                             audio-format)
        source-data-line (^SourceDataLine AudioSystem/getLine info)
        source-data-line (doto ^SourceDataLine source-data-line
                           (.open audio-format)
                           (.start))]
    
    (fn
      ([])
      ([read-bytes]
       (.drain source-data-line)
       (.close source-data-line)
       read-bytes)
      ([read-bytes buf]
       ;; (.drain  ^SourceDataLine source-data-line)
       (let [result
             (+ read-bytes
                (.write ^SourceDataLine source-data-line buf 0 (alength buf)))]
         
         result)
       ))))

;; /* Renders multiple audio frames in one go.
;;  *
;;  * One frame is defined as a sample of left and right channels, interleaved.
;;  * I.e. int16_t buf[4] = { l, r, l, r }; would be 2 frames.
;;  * Only one of the audio callbacks must ever be used.
;;  */
(declare sound-player)
(def batch (atom nil))

(defn audio-sample-batch-callback [audioq]
  (fn [data num_frames]
    (.put ^LinkedBlockingQueue audioq
          (.getByteArray (.getPointer data)
                         0 (* 2 ;; channels
                              2 ;; 2 bytes per channel
                              num_frames)))
    
    num_frames))



(defn input-poll []
  ;; do nothing
  )

(defn input-state-callback [controls]  
  (fn [port device index id]
    (if (= port 0)
      (if (get @controls
               (c/device-name id))
        1
        0)
      0)))

(defn game-info [path]
  (let [game (retro_game_infoByReference.)]
    (doto game
      (write-string "path" path)
      (.writeField "data" nil)
      (.writeField "size" 0)
      (.writeField "meta" nil))))

(def keymap
  {65 :RETRO_DEVICE_ID_JOYPAD_LEFT
   87 :RETRO_DEVICE_ID_JOYPAD_UP
   83 :RETRO_DEVICE_ID_JOYPAD_DOWN
   68 :RETRO_DEVICE_ID_JOYPAD_RIGHT
   74 :RETRO_DEVICE_ID_JOYPAD_A
   75 :RETRO_DEVICE_ID_JOYPAD_B
   
   32 :RETRO_DEVICE_ID_JOYPAD_START
   10 :RETRO_DEVICE_ID_JOYPAD_SELECT
   257 :RETRO_DEVICE_ID_JOYPAD_SELECT
   })

(defn now-str []
  (.format (java.text.SimpleDateFormat. "yyyy-MM-dd_HH-mm-ss")
           (java.util.Date.)))


(defn main-view [video-pixmap* controls*]
  (when-let [pm @video-pixmap*]
    (ui/on-key-event
     (fn [key scancode action mods]

       (if-let [k (get keymap key)]
         (swap! controls* assoc k
                (or (= :press action)
                    (= :repeat action))))
       nil)
     (ui/scale 3 3 pm)
     #_(ui/image img
                 [800 nil]))))

(def gamepad-axes
  {:GLFW_GAMEPAD_AXIS_LEFT_X   0
   :GLFW_GAMEPAD_AXIS_LEFT_Y   1
   :GLFW_GAMEPAD_AXIS_RIGHT_X   2
   :GLFW_GAMEPAD_AXIS_RIGHT_Y   3
   :GLFW_GAMEPAD_AXIS_LEFT_TRIGGER   4
   :GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER   5})
(def axes-name
  (into {}
        (map (fn [[k v]]
               [v k]))
        gamepad-axes))

(def gamepad-buttons
  {:GLFW_GAMEPAD_BUTTON_A   0
   :GLFW_GAMEPAD_BUTTON_B   1
   :GLFW_GAMEPAD_BUTTON_X   2
   :GLFW_GAMEPAD_BUTTON_Y   3
   :GLFW_GAMEPAD_BUTTON_LEFT_BUMPER   4
   :GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER   5
   :GLFW_GAMEPAD_BUTTON_BACK   6
   :GLFW_GAMEPAD_BUTTON_START   7
   :GLFW_GAMEPAD_BUTTON_GUIDE   8
   :GLFW_GAMEPAD_BUTTON_LEFT_THUMB   9
   :GLFW_GAMEPAD_BUTTON_RIGHT_THUMB   10
   :GLFW_GAMEPAD_BUTTON_DPAD_UP   11
   :GLFW_GAMEPAD_BUTTON_DPAD_RIGHT   12
   :GLFW_GAMEPAD_BUTTON_DPAD_DOWN   13
   :GLFW_GAMEPAD_BUTTON_DPAD_LEFT   14
   ;; :GLFW_GAMEPAD_BUTTON_LAST   GLFW_GAMEPAD_BUTTON_DPAD_LEFT
   ;; :GLFW_GAMEPAD_BUTTON_CROSS   GLFW_GAMEPAD_BUTTON_A
   ;; :GLFW_GAMEPAD_BUTTON_CIRCLE   GLFW_GAMEPAD_BUTTON_B
   ;; :GLFW_GAMEPAD_BUTTON_SQUARE   GLFW_GAMEPAD_BUTTON_X
   ;; :GLFW_GAMEPAD_BUTTON_TRIANGLE   GLFW_GAMEPAD_BUTTON_Y
   })
(def gamepad-button-name
  (into {}
        (map (fn [[k v]]
               [v k]))
        gamepad-buttons))

(defn sleep
  ([nanos]
   ;; todo: experimentally determine
   ;;       a good value for max-sleep-sleep
   (sleep 9000000 nanos))
  ([max-sleep-sleep nanos]
   (let [start (System/nanoTime)
         sleep-sleep (if (> nanos max-sleep-sleep)
                       max-sleep-sleep
                       0)
         spin-sleep (if (> nanos max-sleep-sleep)
                      (- nanos max-sleep-sleep)
                      nanos)]
     (when (pos? sleep-sleep)
       (Thread/sleep (clojure.math/floor-div sleep-sleep 1000000)
                     (mod sleep-sleep 1000000)))
     (while (< (- (System/nanoTime)
                  start)
               nanos)
       (Thread/onSpinWait)))))


(defn play-game* [full-path opts]
  (let [{:keys [run-with-close-handler render-frame ->repaint!]} opts
        running? (atom true)
        refs (atom #{})
        ref! (fn [o]
               (swap! refs conj o)
               o)

        _ (retro/retro_set_environment set-environment)
        _ (retro/retro_init)

        video-pixmap* (atom nil)
        _ (retro/retro_set_video_refresh
           (ref! (video-refresh-callback render-frame video-pixmap*)))
        _ (retro/retro_set_audio_sample audio-sample)


        audioq (LinkedBlockingQueue. 1)
        controls* (atom {})
        _ (retro/retro_set_input_poll input-poll)
        _ (retro/retro_set_input_state
           (ref! (input-state-callback controls*)))

        

        _ (retro/retro_set_audio_sample_batch
           (ref! (audio-sample-batch-callback audioq)))

        _ (retro/retro_load_game
           (ref!
            (game-info full-path)))

        av-info (retro/get-av-info)
        fps (-> av-info
                :timing
                :fps)
        frame-nanos (long (* 1e9 (/ 1 fps)))
        sample-rate (-> av-info
                        :timing
                        :sample-rate)

        audio-thread (doto (Thread.
                            (fn []
                              (let [play-audio (play-sound sample-rate)]
                                (while @running?
                                  (play-audio 0 (.take audioq))))))
                       (.start))

        wwidth (-> av-info
                   :geometry
                   :base-width
                   (* 3))
        wheight (-> av-info
                   :geometry
                   :base-height
                   (* 3))
        winfo (run-with-close-handler
               #(main-view video-pixmap* controls*)
               {:window-start-width wwidth
                :window-start-height wheight}
               (fn []
                 (reset! running? false)))
        repaint! (->repaint! winfo)]
    (loop []
      (when @running?
        (let [start (System/nanoTime)]
          (retro/retro_run)
          
          (repaint!)
          
          (sleep (- frame-nanos
                    (- (System/nanoTime)
                       start))))
        (recur)))
    (retro/retro_deinit)

    ;; hang on to refs till the end
    (reset! refs nil)
    nil))

(defn play-game [full-path opts]
  (let [thread
        (Thread. #(play-game* full-path opts))]
    (.setPriority thread Thread/MAX_PRIORITY)
    (.start thread)
    (.join thread)))

(comment
  (play-game "Super Mario Bros. 2.nes")
  (play-game "Super Mario World.smc")
  ,)


