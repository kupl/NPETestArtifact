#!/usr/bin/env bash

SETTINGS="/usr/share/maven/conf/settings.xml"
start_index=$(grep -wrn "maven-default-http-blocker" $SETTINGS -A5 -B1 | grep "<mirror>" | cut -d- -f1)
sed_cmd="$start_index i <!--" 
sed -i "$sed_cmd" $SETTINGS

end_index=$(grep -wrn "maven-default-http-blocker" $SETTINGS -A5 -B1 | grep "</mirror>" | cut -d- -f1)
sed_cmd="$((end_index+1)) i -->" 
sed -i "$sed_cmd" $SETTINGS

sed -e "s/\r//g" $SETTINGS > /tmp/settings.xml
mv /tmp/settings.xml $SETTINGS

