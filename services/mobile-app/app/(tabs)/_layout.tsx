import { Tabs } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';

export default function TabLayout() {
    return (
        <Tabs
            screenOptions={{
                headerShown: false,
                tabBarStyle: {
                    backgroundColor: '#0f0c29',
                    borderTopColor: 'rgba(255,255,255,0.1)',
                },
                tabBarActiveTintColor:   '#00ff88',
                tabBarInactiveTintColor: 'rgba(255,255,255,0.4)',
            }}
        >
            <Tabs.Screen
                name="index"
                options={{
                    title: 'Live Feed',
                    tabBarIcon: ({ color, size }) => (
                        <Ionicons name="pulse" size={size} color={color} />
                    ),
                }}
            />
            <Tabs.Screen
                name="search"
                options={{
                    title: 'Search',
                    tabBarIcon: ({ color, size }) => (
                        <Ionicons name="search" size={size} color={color} />
                    ),
                }}
            />
            <Tabs.Screen
                name="settings"
                options={{
                    title: 'Settings',
                    tabBarIcon: ({ color, size }) => (
                        <Ionicons name="settings-outline" size={size} color={color} />
                    ),
                }}
            />
        </Tabs>
    );
}