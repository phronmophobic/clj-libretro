(ns com.phronemophobic.clj-libretro.util
  (:require [clojure.java.io :as io]
            [com.phronemophobic.clj-libretro.api :as retro]
            [com.phronemophobic.clj-libretro.constants :as c])
  (:import java.io.ByteArrayOutputStream
           com.sun.jna.Memory))

(defn load-save
  "Read the serialized game state from the file path `fname` and overwrite the current global game state."
  [iretro fname]
  (let [baos (ByteArrayOutputStream.)]
    (with-open [is (io/input-stream fname)]
      (io/copy is baos))
    (let [buf (.toByteArray baos)]
      (retro/retro_unserialize iretro buf (alength buf)))))

(defn save-game
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

(defn get-state
  "Return a byte array containing the serialized state of the current global game state."
  [iretro]
  (let [size (retro/retro_serialize_size iretro)
        mem (Memory. size)]
    (retro/retro_serialize iretro mem size)
    (let [buf (byte-array size)]
      (.read mem 0 buf 0 size)
      buf)))

(defn load-state
  "Overwrite the current global game state with the contents of `state`.

  `state` should be a byte array containing the serialized game state."
  [iretro state]
  (retro/retro_unserialize iretro state (alength state)))


(def RETRO_MEMORY_SYSTEM_RAM  2)
(def ram-size 0x800)

(defn get-memory
  "Return the current console RAM.

  Note: this is different than the game state."
  [iretro]
  (let [mem (retro/retro_get_memory_data iretro RETRO_MEMORY_SYSTEM_RAM)
        buf (byte-array ram-size)]
    (.read mem 0 buf 0 ram-size)
    buf))

(defn set-environment [cmd data]
  (case (c/name cmd)
    :RETRO_ENVIRONMENT_SET_CONTENT_INFO_OVERRIDE 1

    :RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE
    (do
      ;; (.setInt data 0 (bit-or 1 2))
      0)

    ;; else
    0))

(defn video-refresh-callback [data width height pitch])

(defn audio-sample [left right])

(defn audio-sample-batch-callback [data num_frames]
  num_frames)

(defn input-poll []
  ;; do nothing
  )

(defn input-state-callback [port device index id]
  0)



(defn init! [iretro game-path]
  (retro/retro_set_environment iretro #'set-environment)
  (retro/retro_init iretro)


  (retro/retro_set_video_refresh iretro #'video-refresh-callback)
  (retro/retro_set_audio_sample iretro #'audio-sample)


  (retro/retro_set_input_poll iretro #'input-poll)
  (retro/retro_set_input_state iretro #'input-state-callback)
  (retro/retro_set_audio_sample_batch iretro #'audio-sample-batch-callback)

  (retro/retro_load_game iretro (retro/game-info game-path)))

(defn deinit! [iretro]
  (retro/retro_deinit iretro))


