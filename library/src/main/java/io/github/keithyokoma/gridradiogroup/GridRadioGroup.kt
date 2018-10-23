package io.github.keithyokoma.gridradiogroup

import android.annotation.TargetApi
import android.content.Context
import android.content.res.TypedArray
import android.os.Build
import android.support.annotation.IdRes
import android.support.v7.widget.GridLayout
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.autofill.AutofillManager
import android.widget.CompoundButton
import android.view.ViewStructure
import android.view.autofill.AutofillValue
import android.widget.RadioButton


/**
 * @author KeithYokoma
 */
class GridRadioGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : GridLayout(context, attrs, defStyle) {
    private val childOnCheckedChangeListener: CompoundButton.OnCheckedChangeListener
    private val passThroughHierarchyChangeListener: PassThroughHierarchyChangeListener

    var onCheckedChangeListener: OnCheckedChangeListener? = null
    private var checkedId: Int = View.NO_ID
    private var initialCheckedId: Int = View.NO_ID
    private var protectFromCheckedChange: Boolean = false

    init {
        // RadioGroup is important by default, unless app developer overrode attribute.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && importantForAutofill == IMPORTANT_FOR_AUTOFILL_AUTO) {
            importantForAutofill = IMPORTANT_FOR_AUTOFILL_YES
        }
        context.obtainStyledAttributes(attrs, R.styleable.GridRadioGroup, 0, 0).use { array: TypedArray ->
            val id: Int = array.getResourceId(R.styleable.GridRadioGroup_defaultCheckedId, View.NO_ID)
            if (id == View.NO_ID)
                return@use
            initialCheckedId = id
            checkedId = id
        }
        childOnCheckedChangeListener = CheckedStateTracker()
        passThroughHierarchyChangeListener = PassThroughHierarchyChangeListener()
        super.setOnHierarchyChangeListener(passThroughHierarchyChangeListener)
    }

    override fun setOnHierarchyChangeListener(listener: OnHierarchyChangeListener?) {
        passThroughHierarchyChangeListener.onHierarchyChangeListener = listener
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        if (checkedId != View.NO_ID) {
            protectFromCheckedChange = true
            setCheckedStateForView(checkedId, true)
            protectFromCheckedChange = false
            setCheckedId(checkedId)
        }
    }

    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        if (child is RadioButton) {
            if (child.isChecked) {
                protectFromCheckedChange = true
                if (checkedId != View.NO_ID) {
                    setCheckedStateForView(id, false)
                }
                protectFromCheckedChange = false
                setCheckedId(child.id)
            }
        }
        super.addView(child, index, params)
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        return GridRadioGroup.LayoutParams(context, attrs)
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean {
        return p is GridRadioGroup.LayoutParams
    }

    override fun generateDefaultLayoutParams(): GridLayout.LayoutParams {
        return GridLayout.LayoutParams()
    }

    override fun getAccessibilityClassName(): CharSequence {
        return GridRadioGroup::class.java.name
    }

    @TargetApi(value = Build.VERSION_CODES.O)
    override fun onProvideAutofillStructure(structure: ViewStructure?, flags: Int) {
        super.onProvideAutofillStructure(structure, flags)
        structure?.setDataIsSensitive(checkedId != initialCheckedId)
    }

    @TargetApi(value = Build.VERSION_CODES.O)
    override fun autofill(value: AutofillValue?) {
        if (!isEnabled) {
            return
        }
        if (value?.isList == false) {
            return
        }
        val index: Int = value?.listValue ?:return
        val child: View = getChildAt(index) ?: return

        check(child.id)
    }

    @TargetApi(value = Build.VERSION_CODES.O)
    override fun getAutofillType(): Int {
        return if (isEnabled) {
            View.AUTOFILL_TYPE_LIST
        } else {
            View.AUTOFILL_TYPE_NONE
        }
    }

    @TargetApi(value = Build.VERSION_CODES.O)
    override fun getAutofillValue(): AutofillValue? {
        if (!isEnabled) {
            return null
        }
        val count = childCount
        (0 until count).map { index: Int ->
            val child = getChildAt(index)
            if (child.id == checkedId) {
                return AutofillValue.forList(index)
            }
        }
        return null
    }

    fun check(@IdRes id: Int) {
        if (id != View.NO_ID && id == checkedId) {
            return
        }
        if (checkedId != View.NO_ID) {
             setCheckedStateForView(checkedId, false)
        }
        if (id != View.NO_ID) {
            setCheckedStateForView(id, true)
        }
        setCheckedId(id)
    }

    @IdRes
    fun getCheckedRadioButtonId(): Int {
        return checkedId
    }

    fun clearCheck() {
        check(View.NO_ID)
    }

    private fun setCheckedId(@IdRes id: Int) {
        val changed = checkedId == id
        checkedId = id

        onCheckedChangeListener?.onCheckedChange(this, checkedId)
        if (changed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(AutofillManager::class.java)?.let { am: AutofillManager ->
               am.notifyValueChanged(this)
            }
        }
    }

    private fun setCheckedStateForView(@IdRes id: Int, checked: Boolean) {
        val view = findViewById<View>(id)
        if (view is RadioButton) {
            view.isChecked = checked
        }
    }

    interface OnCheckedChangeListener {
        fun onCheckedChange(group: GridRadioGroup, @IdRes checkedId: Int)
    }

    class LayoutParams : GridLayout.LayoutParams {
        constructor() : super()
        constructor(rowSpec: GridLayout.Spec, columnSpec: GridLayout.Spec) : super(rowSpec, columnSpec)
        constructor(c: Context, attrs: AttributeSet?) : super(c, attrs)
        constructor(p: ViewGroup.LayoutParams) : super(p)
        constructor(source: MarginLayoutParams) : super(source)
        constructor(source: GridLayout.LayoutParams) : super(source)

        override fun setBaseAttributes(a: TypedArray?, widthAttr: Int, heightAttr: Int) {
            width = if (a?.hasValue(widthAttr) == true) {
                a.getLayoutDimension(widthAttr, "layout_width")
            } else {
                WRAP_CONTENT
            }

            height = if (a?.hasValue(heightAttr) == true) {
                a.getLayoutDimension(heightAttr, "layout_height")
            } else {
                WRAP_CONTENT
            }
        }
    }

    private inner class CheckedStateTracker : CompoundButton.OnCheckedChangeListener {
        override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
            if (buttonView == null) {
                return
            }
            if (protectFromCheckedChange) {
                return
            }
            protectFromCheckedChange = true
            if (checkedId != View.NO_ID) {
                 setCheckedStateForView(checkedId, false) // remove check
            }
            protectFromCheckedChange = false
            setCheckedId(buttonView.id)
        }
    }

    private inner class PassThroughHierarchyChangeListener : ViewGroup.OnHierarchyChangeListener {
        var onHierarchyChangeListener: ViewGroup.OnHierarchyChangeListener? = null

        override fun onChildViewRemoved(parent: View?, child: View?) {
            if (parent == this@GridRadioGroup && child is RadioButton) {
                child.setOnCheckedChangeListener(null)
            }
            onHierarchyChangeListener?.onChildViewRemoved(parent, child)
        }

        override fun onChildViewAdded(parent: View?, child: View?) {
            if (parent == this@GridRadioGroup && child is RadioButton) {
                var id: Int = child.id
                if (id == View.NO_ID) {
                    id = View.generateViewId()
                    child.id = id
                }
                child.setOnCheckedChangeListener(childOnCheckedChangeListener)
            }
            onHierarchyChangeListener?.onChildViewAdded(parent, child)
        }
    }
}