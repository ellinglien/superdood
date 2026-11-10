/** Lasso to Superdood runs without a view (its button's showType is 0); this one only closes. */
import React from 'react';
import {Pressable, Text, View} from 'react-native';
import {PluginManager} from 'sn-plugin-lib';

export default function App(): React.JSX.Element {
  return (
    <View style={{flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#ffffff'}}>
      <Pressable onPress={() => PluginManager.closePluginView()}>
        <Text style={{fontSize: 24, color: '#000000'}}>Close</Text>
      </Pressable>
    </View>
  );
}
