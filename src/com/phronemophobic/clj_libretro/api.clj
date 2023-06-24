(ns com.phronemophobic.clj-libretro.api
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.java.io :as io]
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
  (-> (with-open [rdr (io/reader
                       (io/resource
                        "com/phronemophobic/clj-libretro/api.edn"))
                  rdr (java.io.PushbackReader. rdr)]
        (edn/read rdr))
      #_((requiring-resolve 'com.phronemophobic.clong.clang/easy-api) "/Users/adrian/workspace/libretro-super/libretro-fceumm/src/drivers/libretro/libretro-common/include/libretro.h")
      (gen/replace-forward-references)))


(defprotocol IRetro
  (retro_set_environment [this env-callback]
    "Sets callbacks. retro_set_environment() is guaranteed to be called
before retro_init().

The rest of the set_* functions are guaranteed to have been called
before the first call to retro_run() is made.")

  (retro_set_video_refresh [this video-refresh-callback])

  (retro_set_audio_sample [this audio-sample-callback])

  (retro_set_audio_sample_batch [this audo-sample-batch-callback])

  (retro_set_input_poll [this poll-input-callback])

  (retro_set_input_state [this input-state-callback])


  (retro_init [this]
    "Library global initialization")

  (retro_deinit [this]
    "Library global deinitialization")
  (retro_api_version [this]
    "Must return RETRO_API_VERSION. Used to validate ABI when the API is revised.")

  (retro_get_system_info [this info]
    "Gets statically known system info. Pointers provided in *info
must be statically allocated.
Can be called at any time, even before retro_init().")

  (retro_get_system_av_info [this info]
    "Gets information about system audio/video timings and geometry.
Can be called only after retro_load_game() has successfully completed.
NOTE: The implementation of this function might not initialize every
variable if needed.
E.g. geom.aspect_ratio might not be initialized if core doesn't
desire a particular aspect ratio.")

  (retro_set_controller_port_device [this port device]
    "Sets device to be used for player 'port'.
By default, RETRO_DEVICE_JOYPAD is assumed to be plugged into all
available ports.
Setting a particular device type is not a guarantee that libretro cores
will only poll input based on that particular device type. It is only a
hint to the libretro core when a core cannot automatically detect the
appropriate input device type on its own. It is also relevant when a
core can change its behavior depending on device type.
 *
As part of the core's implementation of retro_set_controller_port_device,
the core should call RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS to notify the
frontend if the descriptions for any controls have changed as a
result of changing the device type.
")

  (retro_reset [this]
    "Resets the current game.")

  (retro_run [this]
   "Runs the game for one video frame.
During retro_run(), input_poll callback must be called at least once.
 *
If a frame is not rendered for reasons where a game \"dropped\" a frame,
this still counts as a frame, and retro_run() should explicitly dupe
a frame if GET_CAN_DUPE returns true.
In this case, the video callback can take a NULL argument for data.
")

  (retro_serialize_size [this]
   "Returns the amount of data the implementation requires to serialize
internal state (save states).
Between calls to retro_load_game() and retro_unload_game(), the
returned size is never allowed to be larger than a previous returned
value, to ensure that the frontend can allocate a save state buffer once.
")

  (retro_serialize [this data size]
   "Serializes internal state. If failed, or size is lower than
retro_serialize_size(), it should return false, true otherwise.")

  (retro_unserialize [this data size])

  (retro_cheat_reset [this])

  (retro_cheat_set [this index enabled code])

  (retro_load_game [this game]
    "Loads a game.
Return true to indicate successful loading and false to indicate load failure.
")

  (retro_load_game_special [this game_type info num_info]
    "Loads a \"special\" kind of game. Should not be used,
except in extreme cases.")

  (retro_unload_game [this]
    "Unloads the currently loaded game. Called before retro_deinit(void).")

  (retro_get_region [this]
    "Gets region of game.")

  (retro_get_memory_data [this id]
    "Gets region of memory.")

  (retro_get_memory_size [this id]))

(def struct-prefix "com.phronemophobic.clj_libretro.api.structs")

(gen/def-enums (:enums api))
(gen/def-structs (:structs api) struct-prefix) 

(gen/import-structs! api)

(let [struct-prefix (gen/ns-struct-prefix *ns*)]
  (defmacro import-structs! []
    `(gen/import-structs! api ~struct-prefix)))

(defn get-av-info [iretro]
  (let [s (retro_system_av_infoByReference.)]
    (retro_get_system_av_info iretro s)
    (let [timing (.readField s "timing")
          geometry (.readField s "geometry")]
      {:timing {:fps (.readField timing "fps")
                :sample-rate (.readField timing "sample_rate")}
       :geometry {:base-width (.readField geometry "base_width")
                  :base-height (.readField geometry "base_height")
                  :max-width (.readField geometry "max_width")
                  :max-height (.readField geometry "max_height")
                  :aspect-ratio (.readField geometry "aspect_ratio")}})))

