schedule clear runelegend_test:restart/wave_due
data remove storage runelegend_test:restart pending
data remove storage runelegend_test:restart active
scoreboard players set #phase rl_restart_phase 0
execute at @e[type=minecraft:marker,tag=runelegend_test.restart_arena,limit=1] run kill @e[type=!minecraft:player,tag=runelegend_test.round_mob,distance=..3]
execute at @e[type=minecraft:marker,tag=runelegend_test.restart_arena,limit=1] run setblock ~ ~ ~ minecraft:air
