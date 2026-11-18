execute unless data storage runelegend_test:player_probe active run return 0
$data modify storage runelegend_test:player_probe events append value {type:"round_ended",round:$(round)L,reason:"$(reason)",identities:$(identities)}
