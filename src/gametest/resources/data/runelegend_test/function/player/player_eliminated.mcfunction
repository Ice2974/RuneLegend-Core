execute unless data storage runelegend_test:player_probe active run return 0
$data modify storage runelegend_test:player_probe events append value {type:"eliminated",round:$(round)L,player_uuid:"$(player_uuid)",anchor:$(anchor),reason:"$(reason)",reason_id:"$(reason_id)",previous_identity:"$(previous_identity)"}
