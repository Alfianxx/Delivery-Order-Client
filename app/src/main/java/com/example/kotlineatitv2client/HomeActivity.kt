package com.example.kotlineatitv2client

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.NavController
import com.example.kotlineatitv2client.Common.Common
import com.example.kotlineatitv2client.Database.CartDataSource
import com.example.kotlineatitv2client.Database.CartDatabase
import com.example.kotlineatitv2client.Database.LocalCartDataSource
import com.example.kotlineatitv2client.EventBus.*
import com.example.kotlineatitv2client.Model.CategoryModel
import com.example.kotlineatitv2client.Model.FoodModel
import com.example.kotlineatitv2client.Model.PopularCategoryModel
import com.example.kotlineatitv2client.Model.UserModel
import com.example.kotlineatitv2client.Remote.ICloudFunctions
import com.example.kotlineatitv2client.Remote.RetrofitCloudClient
import com.google.android.gms.common.api.Status
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import dmax.dialog.SpotsDialog
import io.paperdb.Paper
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.internal.operators.single.SingleObserveOn
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.app_bar_home.*
import kotlinx.android.synthetic.main.layout_place_order.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*
import kotlin.collections.HashMap

class HomeActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var cartDataSource: CartDataSource
    private lateinit var navController: NavController
    private var drawer: DrawerLayout? = null
    private var dialog: AlertDialog? = null
    private var navView: NavigationView? = null

    private var placeSelected: Place? = null
    private var places_fragment: AutocompleteSupportFragment? = null
    private lateinit var placeClient: PlacesClient
    private var placeFields = Arrays.asList(
        Place.Field.ID,
        Place.Field.NAME,
        Place.Field.ADDRESS,
        Place.Field.LAT_LNG
    )

    private var cloudsFunction: ICloudFunctions? = null
    private val compositeDisaple = CompositeDisposable()

    private var menuItemClick = -1

    override fun onResume() {
        super.onResume()
        //countCartItem()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        dialog = SpotsDialog.Builder().setContext(this).setCancelable(false).build()

        cartDataSource = LocalCartDataSource(CartDatabase.getInstance(this).cartDAO())

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val fab: FloatingActionButton = findViewById(R.id.fab)
        fab.setOnClickListener { view ->
            navController.navigate(R.id.nav_cart)
        }

        fab_chat.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        drawer = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_restaurant,
                R.id.nav_menu, R.id.nav_food_detail,
                R.id.nav_cart
            ), drawer
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView!!.setupWithNavController(navController)

        var headerView = navView!!.getHeaderView(0)
        var txt_user = headerView.findViewById<TextView>(R.id.txt_user)
        Common.setSpanString("Hello, ", Common.currentUser!!.name, txt_user)

        navView!!.setNavigationItemSelectedListener(object :
            NavigationView.OnNavigationItemSelectedListener {
            override fun onNavigationItemSelected(p0: MenuItem): Boolean {

                p0.isChecked = true
                drawer!!.closeDrawers()
                if (p0.itemId == R.id.nav_sign_out) {
                    signOut()
                } else if (p0.itemId == R.id.nav_restaurant) {
                    if (menuItemClick != p0.itemId)
                        navController.navigate(R.id.nav_restaurant)
                }else if (p0.itemId == R.id.nav_cart) {
                    if (menuItemClick != p0.itemId) {
                        EventBus.getDefault().postSticky(MenuInflateEvent(true))
                        navController.navigate(R.id.nav_cart)
                    }
                } else if (p0.itemId == R.id.nav_menu) {
                    if (menuItemClick != p0.itemId) {
                        EventBus.getDefault().postSticky(MenuInflateEvent(true))
                        navController.navigate(R.id.nav_menu)
                    }
                } else if (p0.itemId == R.id.nav_view_order) {
                    if (menuItemClick != p0.itemId) {
                        EventBus.getDefault().postSticky(MenuInflateEvent(true))
                        navController.navigate(R.id.nav_view_order)
                    }
                } else if (p0.itemId == R.id.nav_update_info) {

                    showUpdateInfoDialog()

                } else if (p0.itemId == R.id.nav_news) {

                    showNewsDialog()

                }

                menuItemClick = p0!!.itemId
                return true
            }

        })

        initPlacesClient()

        //Hide Cart Button, because we don't need it in restaurant list
        EventBus.getDefault().postSticky(HideFABCart(true))
    }

    private fun showNewsDialog() {
        Paper.init(this)
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("NEWS SYSTEM")
        builder.setMessage("Do you want to subscribe news?")

        val itemView = LayoutInflater.from(this@HomeActivity)
            .inflate(R.layout.layout_subscribe_news, null)
        val ckb_news = itemView.findViewById<View>(R.id.ckb_subscribe_news) as CheckBox
        val isSubscribeNews = Paper.book().read<Boolean>(Common.currentRestaurant!!.uid, false)
        if (isSubscribeNews) ckb_news.isChecked = true
        builder.setNegativeButton("CANCEL", { dialogInterface, i -> dialogInterface.dismiss() })
        builder.setPositiveButton("SEND", { dialogInterface, i ->
            if (ckb_news.isChecked) {
                Paper.book().write(Common.currentRestaurant!!.uid, true)
                FirebaseMessaging.getInstance().subscribeToTopic(Common.getNewsTopic())
                    .addOnFailureListener { e: Exception ->
                        Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
                    }
                    .addOnSuccessListener { aVoid: Void? ->
                        Toast.makeText(this, "Subscribe success!", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Paper.book().delete(Common.currentRestaurant!!.uid)
                FirebaseMessaging.getInstance().unsubscribeFromTopic(Common.getNewsTopic())
                    .addOnFailureListener { e: Exception ->
                        Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
                    }
                    .addOnSuccessListener { aVoid: Void? ->
                        Toast.makeText(this, "Unsubscribe success!", Toast.LENGTH_SHORT).show()
                    }
            }
        })
        builder.setView(itemView)
        val dialog = builder.create()
        dialog.show()
    }

    private fun initPlacesClient() {
        Places.initialize(this, getString(R.string.google_maps_key))
        placeClient = Places.createClient(this)
    }

    private fun showUpdateInfoDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("REGISTER")
        builder.setMessage("Please Fill Information")

        val itemView =
            LayoutInflater.from(this@HomeActivity).inflate(R.layout.layout_register, null)

        val edt_name = itemView.findViewById<EditText>(R.id.edt_name)
        val edt_phone = itemView.findViewById<EditText>(R.id.edt_phone)
        val txt_address = itemView.findViewById<TextView>(R.id.txt_address_detail)

        places_fragment = supportFragmentManager.findFragmentById(R.id.places_autocomplete_fragment)
                as AutocompleteSupportFragment
        places_fragment!!.setPlaceFields(placeFields)
        places_fragment!!.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(p0: Place) {
                placeSelected = p0
                txt_address.text = placeSelected!!.address
            }

            override fun onError(p0: Status) {
                Toast.makeText(this@HomeActivity, "" + p0.statusMessage, Toast.LENGTH_SHORT).show()
            }

        })

        //set
        edt_phone.setText(Common!!.currentUser!!.phone)
        txt_address.setText(Common.currentUser!!.address)
        edt_name.setText(Common!!.currentUser!!.name)

        builder.setView(itemView)
        builder.setNegativeButton("CANCEL") { dialogInterface, i -> dialogInterface.dismiss() }
        builder.setPositiveButton("UPDATE") { dialogInterface, i ->

            if (placeSelected != null) {
                if (TextUtils.isDigitsOnly(edt_name.text.toString())) {
                    Toast.makeText(this@HomeActivity, "Please Enter Your Name", Toast.LENGTH_SHORT)
                        .show()
                    return@setPositiveButton
                }

                val update_data = HashMap<String, Any>()
                update_data.put("name", edt_name.text.toString())
                update_data.put("address", txt_address.text.toString())
                update_data.put("lat", placeSelected!!.latLng!!.latitude)
                update_data.put("lng", placeSelected!!.latLng!!.longitude)

                FirebaseDatabase.getInstance()
                    .getReference(Common.USER_REFERENCE)
                    .child(Common.currentUser!!.uid!!)
                    .updateChildren(update_data)
                    .addOnFailureListener {
                        Toast.makeText(this@HomeActivity, it.message, Toast.LENGTH_SHORT).show()
                    }
                    .addOnSuccessListener {
                        Common.currentUser!!.name = update_data["name"].toString()
                        Common.currentUser!!.address = update_data["address"].toString()
                        Common.currentUser!!.lat = update_data["lat"].toString().toDouble()
                        Common.currentUser!!.lng = update_data["lng"].toString().toDouble()

                        Toast.makeText(this@HomeActivity, "Update Info Success", Toast.LENGTH_SHORT)
                            .show()
                    }
            } else {
                Toast.makeText(this@HomeActivity, "Please select address", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        //PENTING! TAMPILKAN PESAN DIALOG
        val dialog = builder.create()
        dialog.setOnDismissListener {
            val fragmentTransaction = supportFragmentManager.beginTransaction()
            fragmentTransaction.remove(places_fragment!!)
            fragmentTransaction.commit()
        }
        dialog.show()
    }

    private fun signOut() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Sign out")
            .setMessage("Do you really want to exit?")
            .setNegativeButton("CANCEL", { dialogInterface, _ -> dialogInterface.dismiss() })
            .setPositiveButton("OK") { dialogInterface, _ ->
                Common.foodSelected = null
                Common.categorySelected = null
                Common.currentUser = null
                FirebaseAuth.getInstance().signOut()

                val intent = Intent(this@HomeActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }

        val dialog = builder.create()
        dialog.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.home, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        EventBus.getDefault().removeAllStickyEvents()
        EventBus.getDefault().unregister(this)
        compositeDisaple.clear()
        super.onStop()
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onCategorySelected(event: CategoryClick) {
        if (event.isSuccess) {
            //Toast.makeText(this,"Click to"+event.category.name,Toast.LENGTH_SHORT).show()
            findNavController(R.id.nav_host_fragment).navigate(R.id.nav_food_list)
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onFoodSelected(event: FoodItemClick) {
        if (event.isSuccess) {

            findNavController(R.id.nav_host_fragment).navigate(R.id.nav_food_detail)
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onHideFABEvent(event: HideFABCart) {
        if (event.isHide) {
            fab.hide()
            fab_chat.hide()
        } else {
            fab.show()
            fab_chat.show()
        }

    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onCountCartEvent(event: CountCartEvent) {
        if (event.isSuccess) {
            if (Common.currentRestaurant != null)
                countCartItem()
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onPopularFoodItemClick(event: PopularFoodItemClick) {
        if (event.popularCategoryModel != null) {
            dialog!!.show()

            FirebaseDatabase.getInstance()
                .getReference(Common.RESTAURANT_REF)
                .child(Common.currentRestaurant!!.uid)
                .child(Common.CATEGORY_REF)
                .child(event.popularCategoryModel!!.menu_id!!)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onCancelled(p0: DatabaseError) {
                        dialog!!.dismiss()
                        Toast.makeText(this@HomeActivity, "" + p0.message, Toast.LENGTH_SHORT)
                            .show()
                    }

                    override fun onDataChange(p0: DataSnapshot) {
                        if (p0.exists()) {
                            Common.categorySelected = p0.getValue(CategoryModel::class.java)
                            Common.categorySelected!!.menu_id = p0.key

                            //Load Food
                            FirebaseDatabase.getInstance()
                                .getReference(Common.RESTAURANT_REF)
                                .child(Common.currentRestaurant!!.uid)
                                .child(Common.CATEGORY_REF)
                                .child(event.popularCategoryModel!!.menu_id!!)
                                .child("foods")
                                .orderByChild("id")
                                .equalTo(event.popularCategoryModel.food_id)
                                .limitToLast(1)
                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onCancelled(p0: DatabaseError) {
                                        dialog!!.dismiss()
                                        Toast.makeText(
                                            this@HomeActivity,
                                            "" + p0.message,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }

                                    override fun onDataChange(p0: DataSnapshot) {
                                        if (p0.exists()) {
                                            for (foodSnapshot in p0.children) {
                                                Common.foodSelected =
                                                    foodSnapshot.getValue(FoodModel::class.java)
                                                Common.foodSelected!!.key = foodSnapshot.key
                                            }
                                            navController!!.navigate(R.id.nav_food_detail)
                                        } else {
                                            Toast.makeText(
                                                this@HomeActivity,
                                                "Item doesn't exists",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        dialog!!.dismiss()
                                    }

                                })
                        } else {
                            dialog!!.dismiss()
                            Toast.makeText(
                                this@HomeActivity,
                                "Item doesn't exists",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                })
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onBestDealFoodItemClick(event: BestDealItemClick) {
        if (event.model != null) {
            dialog!!.show()

            FirebaseDatabase.getInstance()
                .getReference(Common.RESTAURANT_REF)
                .child(Common.currentRestaurant!!.uid)
                .child(Common.CATEGORY_REF)
                .child(event.model!!.menu_id!!)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onCancelled(p0: DatabaseError) {
                        dialog!!.dismiss()
                        Toast.makeText(this@HomeActivity, "" + p0.message, Toast.LENGTH_SHORT)
                            .show()
                    }

                    override fun onDataChange(p0: DataSnapshot) {
                        if (p0.exists()) {
                            Common.categorySelected = p0.getValue(CategoryModel::class.java)
                            Common.categorySelected!!.menu_id = p0.key

                            //Load Food
                            FirebaseDatabase.getInstance()
                                .getReference(Common.RESTAURANT_REF)
                                .child(Common.currentRestaurant!!.uid)
                                .child(Common.CATEGORY_REF)
                                .child(event.model!!.menu_id!!)
                                .child("foods")
                                .orderByChild("id")
                                .equalTo(event.model.food_id)
                                .limitToLast(1)
                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onCancelled(p0: DatabaseError) {
                                        dialog!!.dismiss()
                                        Toast.makeText(
                                            this@HomeActivity,
                                            "" + p0.message,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }

                                    override fun onDataChange(p0: DataSnapshot) {
                                        if (p0.exists()) {
                                            for (foodSnapshot in p0.children) {
                                                Common.foodSelected =
                                                    foodSnapshot.getValue(FoodModel::class.java)
                                                Common.foodSelected!!.key = foodSnapshot.key
                                            }
                                            navController!!.navigate(R.id.nav_food_detail)
                                        } else {
                                            Toast.makeText(
                                                this@HomeActivity,
                                                "Item doesn't exists",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        dialog!!.dismiss()
                                    }

                                })
                        } else {
                            dialog!!.dismiss()
                            Toast.makeText(
                                this@HomeActivity,
                                "Item doesn't exists",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                })
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public fun onMenuItemBack(event: MenuItemBack) {
        menuItemClick = -1
        if (supportFragmentManager.backStackEntryCount > 0)
            supportFragmentManager.popBackStack();
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public fun onRestaurantClick(event: MenuItemEvent) {

        cloudsFunction = RetrofitCloudClient.getInstance(event.restaurantModel.paymentUrl)
            .create(ICloudFunctions::class.java)

//        try {
//            Log.d("abcd", "payment url = ${event.restaurantModel.paymentUrl}")
//            cloudsFunction = RetrofitCloudClient.getInstance(event.restaurantModel.paymentUrl)
//                .create(ICloudFunctions::class.java)
//        } catch (e: Exception) {
//            Log.d("abcd", "onRestaurantClick: ${e.message}")
//        }


//      val headers = HashMap<String,String>()
//      headers.put("Authorization",Common.buildToken(Common.authorizeToken!!))

//      compositeDisaple.add(cloudsFunction!!.getToken(headers)
//                        .subscribeOn(Schedulers.io())
//                        .observeOn(AndroidSchedulers.mainThread())
//                        .subscribe({ braintreeToken ->
//
//                            dialog!!.dismiss()
//                            Common.currentToken = braintreeToken.token
//
//                        },{ throwable ->
//                            dialog!!.dismiss()
//                            Toast.makeText(this@HomeActivity,""+throwable.message,Toast.LENGTH_SHORT).show()
//                        }));

        val bundle = Bundle()
        bundle.putString("restaurant", event.restaurantModel.uid)
        navController.navigate(R.id.nav_menu, bundle)

        EventBus.getDefault().postSticky(MenuInflateEvent(true)) //Show detail menu
        EventBus.getDefault().postSticky(HideFABCart(false)) //Show Cart Button

        countCartItem(); //Fix count cart

    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public fun onInflateMenu(event: MenuInflateEvent) {
        navView!!.menu.clear()

        if (event.isShowDetail)
            navView!!.inflateMenu(R.menu.restaurant_detail_menu)    // Navigation drawer jika membuka toko
        else
            navView!!.inflateMenu(R.menu.activity_home_drawer)      // Navigation drawer sebelum membuka toko
    }

    private fun countCartItem() {
        cartDataSource.countItemInCart(Common.currentUser!!.uid!!, Common.currentRestaurant!!.uid)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : SingleObserver<Int> {
                override fun onSuccess(t: Int) {
                    fab.count = t
                }

                override fun onSubscribe(d: Disposable) {

                }

                override fun onError(e: Throwable) {
                    if (!e.message!!.contains("Query returned empty"))
                        Toast.makeText(
                            this@HomeActivity,
                            "[COUNT CART]" + e.message,
                            Toast.LENGTH_SHORT
                        ).show()
                    else
                        fab.count = 0
                }

            })
    }
}
