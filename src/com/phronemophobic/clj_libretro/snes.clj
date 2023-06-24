(ns com.phronemophobic.clj-libretro.snes
  (:require [com.phronemophobic.clong.gen.jna :as gen]
            [com.phronemophobic.clj-libretro.api :as api]))

(def lib (com.sun.jna.NativeLibrary/getInstance "retro_snes9x"))

(gen/def-functions lib (:functions api/api) api/struct-prefix)

(def iretro
  (reify api/IRetro
    (api/retro_set_environment [_ env-callback]
      (retro_set_environment env-callback))

    (api/retro_set_video_refresh [_ video-refresh-callback]
      (retro_set_video_refresh video-refresh-callback))

    (api/retro_set_audio_sample [_ audio-sample-callback]
      (retro_set_audio_sample audio-sample-callback))

    (api/retro_set_audio_sample_batch [_ audo-sample-batch-callback]
      (retro_set_audio_sample_batch audo-sample-batch-callback))

    (api/retro_set_input_poll [_ poll-input-callback]
      (retro_set_input_poll poll-input-callback))

    (api/retro_set_input_state [_ input-state-callback]
      (retro_set_input_state input-state-callback))

    (api/retro_init [_]
      (retro_init))

    (api/retro_deinit [_]
      (retro_deinit))
    (api/retro_api_version [_]
      (retro_api_version))

    (api/retro_get_system_info [_ info]
      (retro_get_system_info info))

    (api/retro_get_system_av_info [_ info]
      (retro_get_system_av_info info))

    (api/retro_set_controller_port_device [_ port device]
      (retro_set_controller_port_device port device))

    (api/retro_reset [_]
      (retro_reset))

    (api/retro_run [_]
      (retro_run))

    (api/retro_serialize_size [_]
      (retro_serialize_size))

    (api/retro_serialize [_ data size]
      (retro_serialize data size))

    (api/retro_unserialize [_ data size]
      (retro_unserialize data size))

    (api/retro_cheat_reset [_]
      (retro_cheat_reset))

    (api/retro_cheat_set [_ index enabled code]
      (retro_cheat_set index enabled code))

    (api/retro_load_game [_ game]
      (retro_load_game game))

    (api/retro_load_game_special [_ game_type info num_info]
      (retro_load_game_special game_type info num_info))

    (api/retro_unload_game [_]
      (retro_unload_game))

    (api/retro_get_region [_]
      (retro_get_region))

    (api/retro_get_memory_data [_ id]
      (retro_get_memory_data id))

    (api/retro_get_memory_size [_ id]
      (retro_get_memory_size id))))
