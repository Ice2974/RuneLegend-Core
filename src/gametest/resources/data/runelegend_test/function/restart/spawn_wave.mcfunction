scoreboard players set #phase rl_restart_phase 2
execute at @e[type=minecraft:marker,tag=runelegend_test.restart_arena,limit=1] run summon minecraft:husk ~1 ~ ~ {Tags:["runelegend_test.round_mob","runelegend_test.enemy"],DeathLootTable:"minecraft:empty"}
execute at @e[type=minecraft:marker,tag=runelegend_test.restart_arena,limit=1] run setblock ~ ~ ~ minecraft:redstone_block
