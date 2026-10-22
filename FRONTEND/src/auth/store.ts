import type LoginData from '@/models/LoginData';
import type LoginResponseData from '@/models/LoginResponseData';
import type User from '@/models/User';
import { loginUser, logoutUser } from '@/service/Authservice';
import {create} from 'zustand';
import{ persist } from 'zustand/middleware';

const LOCAL_KEY = 'auth_state';

//global auth store
type AuthState = {
    accessToken: string | null;
    user:User | null;
    authStatus: boolean;
    authLoading: boolean;
    
    login: (loginData: LoginData) => Promise<LoginResponseData>;
    logout: () => Promise<void>;
    checkLogin : () => boolean;

    changeLocalLoginData:(
        accessToken: string,
        user: User,authStatus:boolean
    ) => void;
};  


//main logic of auth store
const useAuthStore = create<AuthState>()(
    persist(

        (set,get) => ({
    accessToken: null,
    user: null,
    authStatus: false,
    authLoading: false,

    changeLocalLoginData:(accessToken,user,authStatus) => {
        set({
            accessToken,
            user,
            authStatus,
            
        });
    },

    login: async (loginData) => {
        set({authLoading: true})

       try {
        const loginResponseData = await loginUser(loginData);
        // SECURITY: access token ko console me log mat karo (devtools me
        // token leak ho jata tha — screen share / log extensions se exposed)
        set({
            accessToken: loginResponseData.accessToken,
            user: loginResponseData.user,
            authStatus: true,
        });
        return loginResponseData;
       }
        catch (error) {
            // Error log karo, par token/headers nahi — AxiosError me
            // config.headers.Authorization hota hai
            console.error("Login failed:", error instanceof Error ? error.message : "Unknown error");
            throw error;
        }
        finally {
            set({authLoading: false})
        }
    },
    logout:async()=> {
        // Pehle server ko notify karo — backend access token ko blacklist karta hai.
        // Ye ORDER important hai: agar pehle local state clear kar dete hain to
        // request interceptor ke paas token nahi hota, header attach nahi hota,
        // aur backend logout ke baad bhi access token valid reh jata tha.
        try {
            await logoutUser();
        } catch (error) {
            console.error("Logout request failed:", error instanceof Error ? error.message : "Unknown error");
        } finally {
            // State clear karo — navigate immediately hoga
            set({
                accessToken: null,
                user: null,
                authStatus: false,
                authLoading: false,
            });
        }
    },

    

   checkLogin: () => {
        return !!(get().accessToken && get().authStatus);
      },
}),
        {
            name: LOCAL_KEY,
            partialize: (state) => ({
                user: state.user,
                authStatus: state.authStatus,
                // accessToken persist nahi hoga (security)
            }),
        }
    )
);

export default useAuthStore;
    