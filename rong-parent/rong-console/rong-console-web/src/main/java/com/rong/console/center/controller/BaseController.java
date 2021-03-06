package com.rong.console.center.controller;

import com.rong.admin.module.view.TvAdmFields;
import com.rong.admin.service.AdmColumnService;
import com.rong.admin.service.AdmFieldsService;
import com.rong.common.consts.CommonEnumContainer;
import com.rong.common.exception.CustomerException;
import com.rong.common.module.BaseEntity;
import com.rong.common.module.BaseRequest;
import com.rong.common.module.QueryInfo;
import com.rong.common.module.Result;
import com.rong.common.module.TvPageList;
import com.rong.common.service.BasicService;
import com.rong.common.util.CastUtil;
import com.rong.common.util.CommonUtil;
import com.rong.common.util.JSONUtil;
import com.rong.common.util.StringUtil;
import com.rong.common.util.TableMetaInfoUtil;
import com.rong.console.center.config.AuthenticationInterceptor;
import com.rong.console.center.util.ViewModel;
import com.vitily.mybatis.core.entity.FieldValue;
import com.vitily.mybatis.core.entity.TableMetaInfo;
import com.vitily.mybatis.core.enums.ConditionType;
import com.vitily.mybatis.core.enums.Order;
import com.vitily.mybatis.core.wrapper.PageInfo;
import com.vitily.mybatis.core.wrapper.query.MultiTableQueryWrapper;
import com.vitily.mybatis.core.wrapper.sort.OrderBy;
import com.vitily.mybatis.core.wrapper.update.UpdateWrapper;
import com.vitily.mybatis.util.CompareAlias;
import com.vitily.mybatis.util.Elements;
import com.vitily.mybatis.util.SelectAlias;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Slf4j
public abstract class BaseController<T extends BaseEntity<T>,Q extends BaseRequest<T,Q>,V extends T,S extends BasicService<T,Q,V>>{
	public static final String[] PRO_FILTER_ITEMS={"id","tableHeader","tableAlias","rowWidth","sortable","fieldName","visibleTemplate","colGroup","rowGroup","fixed"};

	@Autowired
	protected AdmFieldsService admFieldsService;
	@Autowired
	protected AdmColumnService admColumnService;
	@Autowired
	protected AuthenticationInterceptor admAspect;
	protected final ViewModel viewModel;
	@Autowired
	protected S service;
	protected Enum fields;
	public BaseController(ViewModel viewModel,Enum fields){
		this.viewModel = viewModel;
		this.fields = fields;
	}
	public void otherEventForListInit(HttpServletRequest request,HttpServletResponse response)throws Exception{}
	public void otherEventForAddInit(HttpServletRequest request,HttpServletResponse response,V be)throws Exception{}
	public void otherEventForEditInit(HttpServletRequest request,HttpServletResponse response,V view)throws Exception{}
	public void otherEventForViewInit(HttpServletRequest request,HttpServletResponse response,V view)throws Exception{}
	/**
	 * ????????????:get
	 */
	@GetMapping(value = "list")
	public String list(HttpServletRequest request,HttpServletResponse response, QueryInfo queryInfo)throws Exception{
		//??????????????????????????????????????? ??????
		otherEventForListInit(request,response);
		//??????????????????
		Long columnId = admColumnService.hashAuthSingle(viewModel.getBasePath()+"list");
		List<TvAdmFields> theads=admFieldsService.getFieldShowByColumnId(columnId);
		request.setAttribute("theads", JSONUtil.toJSONString(theads,PRO_FILTER_ITEMS));
		request.setAttribute("queryInfo", JSONUtil.toJSONString(queryInfo));
		request.setAttribute("hasAddPermission",admAspect.hasPermission(viewModel.getAddAction(),request));
		request.setAttribute("hasEditPermission",admAspect.hasPermission(viewModel.getEditAction(),request));
		request.setAttribute("hasViewPermission",admAspect.hasPermission(viewModel.getViewAction(),request));
		request.setAttribute("hasDelPermission",admAspect.hasPermission(viewModel.getDelOrRecAction(),request));
		request.setAttribute("hasDataGridPermission",admAspect.hasPermission(viewModel.getDataGridAction(),request));
		request.setAttribute("viewModel", this.viewModel);
		return "base/list";
	}

	public void wrapQuery(MultiTableQueryWrapper queryWrapper,HttpServletRequest request){}
	protected MultiTableQueryWrapper multiTableQueryWrapper(HttpServletRequest request){
		return service.getMultiCommonWrapper();
	}
	public MultiTableQueryWrapper getMultiQueryInfo(HttpServletRequest request, QueryInfo<T> queryInfo){
		MultiTableQueryWrapper queryWrapper = multiTableQueryWrapper(request);
		queryInfo.bindQueryFromRequests(request.getParameterMap());
		TableMetaInfo tableInfo = TableMetaInfoUtil.getTableInfoFromControllerClass(getClass());
		for(FieldValue element:queryInfo.getConditionElements()){
			switch (element.getType()){
				case LIKE:
					element = FieldValue.valueOf(element.getField(),"%"+element.getValue()+"%", ConditionType.LIKE);
					break;
				case IN:
				case NOTIN:
					element = FieldValue.valueOf(element.getField(), CastUtil.castArray(element.getValue(),tableInfo.getFieldType(element.getField().getItem())),
							element.getType());
					break;
				default:
					element = FieldValue.valueOf(element.getField(), CastUtil.cast(element.getValue(),tableInfo.getFieldType(element.getField().getItem())),
							element.getType());
					break;
			}
			queryWrapper.where(element);
		}


		PageInfo pageInfo = queryInfo.getPageInfo();

		if(CommonUtil.isNull(pageInfo)){
			pageInfo = new PageInfo();
			queryInfo.setPageInfo(pageInfo);
		}
		queryWrapper.page(pageInfo);
		if(StringUtil.isNotEmpty(pageInfo.getSortField())){
			Order order;
			if(StringUtil.isNotEmpty(pageInfo.getSortDistanct())){
				order = Order.valueOf(pageInfo.getSortDistanct().toUpperCase());
			}else{
				order = Order.DESC;
			}
			OrderBy orderBy = OrderBy.valueOf(order, SelectAlias.valueOf(pageInfo.getSortField()));
			queryWrapper.orderBy(orderBy);
		}
		//wrapper queryInfo
		return queryWrapper;
	}
	/**
	 * ??????,?????????????????????
	 */
	@PostMapping(value = "dataGrid")
	@ResponseBody
	public Result<TvPageList<V>> dataGrid(HttpServletRequest request, HttpServletResponse response, QueryInfo queryInfo)throws Exception{
		MultiTableQueryWrapper queryWrapper = getMultiQueryInfo(request,queryInfo);
		//wrapper queryInfo
		wrapQuery(queryWrapper,request);
		return Result.success(service.selectPageList(queryWrapper),"successfull");
	}
	/**
	 * ?????????????????????
	 * @param queryInfo
	 * @return
	 */
	@RequestMapping("simple-list")
	@ResponseBody
	public Result simpleList(HttpServletRequest request, HttpServletResponse response, QueryInfo queryInfo){
		MultiTableQueryWrapper queryWrapper = getMultiQueryInfo(request,queryInfo);
		wrapQuery(queryWrapper,request);
		return Result.success(service.selectPageList(queryWrapper));
	}

	/**
	 * ????????????????????????
	 * @param request
	 * @param response
	 * @return
	 */
	@GetMapping(value="add")
	public String add(HttpServletRequest request,HttpServletResponse response,V v)throws Exception{
		otherEventForAddInit(request,response,v);
		request.setAttribute("entity", JSONUtil.toJSONString(v));
		request.setAttribute("viewModel", this.viewModel);
		viewModel.setAlterActionUrl(viewModel.getBasePath()+"add");
		return viewModel.getBasePath()+"alter";
	}

	protected void preInsert(HttpServletRequest request, HttpServletResponse response, Q req){}
	/**
	 * ??????????????????
	 * @param request
	 * @param response
	 * @param req
	 * @return
	 */
	@PostMapping(value="add")
	@ResponseBody
	public Result add_post(HttpServletRequest request, HttpServletResponse response, @RequestBody Q req)throws Exception{
		preInsert(request,response,req);
		return Result.success(service.insert(req),"????????????");
	}
	/**
	 * ??????????????????
	 * @param request
	 * @param response
	 * @param id
	 * @return
	 */
	@GetMapping(value="edit")
	public String edit(HttpServletRequest request, HttpServletResponse response, long id)throws Exception{
		V entity = service.selectViewByPrimaryKey(service.getMultiCommonIdWrapper(id));
		otherEventForEditInit(request,response,entity);
		request.setAttribute("entity", JSONUtil.toJSONString(entity));
		request.setAttribute("viewModel", this.viewModel);
		viewModel.setAlterActionUrl(viewModel.getBasePath()+"edit");
		return viewModel.getBasePath()+"alter";
	}
	protected void preUpdate(HttpServletRequest request, HttpServletResponse response, Q req){}
	/**
	 * ????????????
	 * @param request
	 * @param response
	 * @param req
	 * @return
	 */
	@PostMapping(value = "edit")
	@ResponseBody
	public Result edit_post(HttpServletRequest request, HttpServletResponse response, @RequestBody Q req)throws Exception{
		//bean.setReqIp(HttpClientUtil.getIP(request));
		preUpdate(request,response,req);
		if(req.getEntity().getId() != null) {
			return Result.success(service.updateByPrimary(req), "???????????????");
		}else{
			return add_post(request,response,req);
		}
	}
	/**
	 * ??????????????????
	 */
	@GetMapping(value="view")
	public String view(HttpServletRequest request, HttpServletResponse response,Long id)throws Exception{
		V entity = service.selectOneView(service.getMultiCommonWrapper().eq(CompareAlias.valueOf("e.id"),id));
		otherEventForViewInit(request,response,entity);
		request.setAttribute("entity", JSONUtil.toJSONString(entity));
		request.setAttribute("viewModel", this.viewModel);
		return viewModel.getViewAction();
	}
	/**
	 * ??????
	 * @param request
	 * @param response
	 * @return
	 */
	@PostMapping(value = "del-or-rec")
	@ResponseBody
	public Result setToRecycleOrNormal(HttpServletRequest request, HttpServletResponse response, Long[] ids, Boolean deltag)throws Exception{
		if(CommonUtil.isEmpty(ids) || CommonUtil.isNull(deltag)){
			throw new CustomerException("????????????????????????????????????ID??????????????????", CommonEnumContainer.ResultStatus.PARAMETER_IS_NOT_COMPLETE);
		}
		UpdateWrapper wrapper = new UpdateWrapper()
				.update(Elements.valueOf(getEnumByField("deltag"),deltag),
						Elements.valueOf(getEnumByField("updateDate"),new Date())
				);
		wrapper.in(getEnumByField("id"),Arrays.asList(ids));
		service.updateSelectItem(wrapper);
		return Result.success();
	}
	private Enum getEnumByField(String field){
		return Enum.valueOf(fields.getClass(),field);
	}
}
