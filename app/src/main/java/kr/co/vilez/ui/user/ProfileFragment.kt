package kr.co.vilez.ui.user

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.SimpleAdapter
import android.widget.TextView
import androidx.core.content.edit
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import kotlinx.coroutines.*
import kr.co.vilez.R
import kr.co.vilez.data.model.User
import kr.co.vilez.databinding.FragmentProfileBinding
import kr.co.vilez.ui.IntroActivity
import kr.co.vilez.ui.MainActivity
import kr.co.vilez.ui.dialog.*
import kr.co.vilez.ui.profile.CalendarFragment
import kr.co.vilez.ui.profile.InterestFragment
import kr.co.vilez.ui.profile.PointFragment
import kr.co.vilez.ui.profile.SharedListFragment
import kr.co.vilez.util.ApplicationClass
import kr.co.vilez.util.StompClient
import retrofit2.awaitResponse
import java.util.Objects

private const val TAG = "빌리지_ProfileFragment"
class ProfileFragment : Fragment() {
    private lateinit var binding:FragmentProfileBinding
    private lateinit var mainActivity: MainActivity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainActivity = context as MainActivity
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_profile, container, false)
        binding.fragment = this

        binding.user = ApplicationClass.user
        // TODO : 나중엔 저장되어있는 이미지로 변경
        binding.profileImg = "https://www.example.com/image.jpg"
        getUserDetail(ApplicationClass.user.id) // 현재 로그인한 유저 id로 user detail 가져오기

        return binding.root
    }

    private fun getUserDetail(userId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val result =
                ApplicationClass.retrofitUserService.getUserDetail(userId).awaitResponse().body()
            if (result?.flag == "success") {
                val data = result.data[0]
                Log.d(TAG, "user detail 조회 성공, 받아온 user = $data")
                binding.userDetail = data // user detail data binding
            } else {
                Log.d(TAG, "user detail 조회 실패, result:$result")
            }
        }
    }



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initMenus()

        CoroutineScope(Dispatchers.Main).launch {
            delay(100) // 뷰가 다 뜨면 화면에 보여주기
            binding.clProfile.visibility = View.VISIBLE
        }
    }

    fun initMenus() {
        // 나의 공유 메뉴 초기화
        val sharedMenuName = arrayOf("공유 캘린더", "공유 목록", "관심 목록", "포인트 내역")
        val shareSimpleAdapter = ArrayAdapter(mainActivity, android.R.layout.simple_list_item_1, sharedMenuName)
        binding.lvMenuShare.apply {
            divider = null
            adapter = shareSimpleAdapter
            setOnItemClickListener { _, _, i, _ ->
                // addToBackStack(null) 동작 안돼서 ACTIVITY 사용
                moveEditActivity(sharedMenuName[i])
            }
        }
        // 설정 메뉴 초기화
        val settingsMenuName = arrayOf("내 동네 설정")
        val settingsSimpleAdapter =  ArrayAdapter(mainActivity, android.R.layout.simple_list_item_1, settingsMenuName)
        binding.lvMenuSettings.apply {
            divider = null
            adapter = settingsSimpleAdapter
            setOnItemClickListener { _, _, i, _ ->
                moveEditActivity(settingsMenuName[i])
            }
        }

        // 계정 메뉴 초기화
        val accountMenuName = arrayOf("내 정보 수정", "로그아웃")
        val accountSimpleAdapter =  ArrayAdapter(mainActivity, android.R.layout.simple_list_item_1, accountMenuName)
        binding.lvMenuAccount.apply {
            divider = null
            adapter = accountSimpleAdapter
            setOnItemClickListener { _, view, i, _ ->
                if(i == 1) { //로그아웃
                    logout(view)
                } else {
                    moveEditActivity(accountMenuName[i])
                }
            }
        }
    }


    fun login(view:View) {
        Log.d(TAG, "login: adf")
        CoroutineScope(Dispatchers.Main).launch {
            val user = User("test@naver.com", "12345")
            val result = ApplicationClass.retrofitUserService.getLoginResult(user).awaitResponse().body()
            if (result == null) { // 로그인 실패
                Log.d(TAG, "login: 로그인 실패, result:$result")
            } else if(result.flag == "success") {  // 로그인 성공
                val dialog = AlertDialogWithCallback(object : AlertDialogInterface {
                    override fun onYesButtonClick(id: String) {
                        Log.d(TAG, "로그인 성공, 받아온 user = ${result.data[0]}")
                        val userInfo:User = result.data[0]
                        binding.user = userInfo
                    }
                }, "로그인 성공", "")
                dialog.isCancelable = false // 알림창이 띄워져있는 동안 배경 클릭 막기
                dialog.show(mainActivity.supportFragmentManager, "RegisterSucceeded")
            }
        }
    }

    private fun logout(view: View){ // 로그아웃 preference 지우기
        val dialog = ConfirmDialog(object: ConfirmDialogInterface {
            override fun onYesButtonClick(id: String) {
                Log.d(TAG, "logout: 삭제 전 autoLogin = ${ApplicationClass.sharedPreferences.getBoolean("autoLogin",false)}")
                ApplicationClass.sharedPreferences.edit {
                    remove("autoLogin")
                    remove("email")
                    remove("password")
                }

                Log.d(TAG, "logout: 로그아웃 성공")
                Log.d(TAG, "logout: 삭제 후 autoLogin = ${ApplicationClass.sharedPreferences.getBoolean("autoLogin", false)}")

                // 로그아웃 후 로그인 화면이동
                val intent = Intent(mainActivity, LoginActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                startActivity(intent)
            }
        }, "정말로 로그아웃 하시겠습니까?", "")

        dialog.isCancelable = false // 알림창이 띄워져있는 동안 배경 클릭 막기
        dialog.show(mainActivity.supportFragmentManager, "Logout")
    }


    fun getMannerLevel(manner : Int): String {
        return if(manner <= 10) {
            "Lv.1"
        } else if (manner <= 20) {
            "Lv.2"
        } else if (manner <= 30) {
            "Lv.3"
        } else if (manner <= 40) {
            "Lv.4"
        } else {
            "Lv.5"
        }
    }

    fun editProfile(view: View) { // 프로필 이미지, 닉네임 변경하는 곳으로 이동
        moveEditActivity("프로필 수정")
    }

    private fun moveEditActivity(fragment: String) {
        val intent = Intent(mainActivity, ProfileMyShareActivity::class.java)
        intent.putExtra("fragment", fragment)
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        mainActivity.startActivity(intent)
    }

}