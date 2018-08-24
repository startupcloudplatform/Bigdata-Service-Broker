/*
 * Copyright 2018 by ONYCOM,INC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.onycom.ankusservicebroker;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.openpaas.servicebroker.exception.ServiceBrokerException;
import org.openpaas.servicebroker.exception.ServiceDefinitionDoesNotExistException;
import org.openpaas.servicebroker.exception.ServiceInstanceBindingExistsException;
import org.openpaas.servicebroker.exception.ServiceInstanceDoesNotExistException;
import org.openpaas.servicebroker.exception.ServiceInstanceExistsException;
import org.openpaas.servicebroker.model.Catalog;
import org.openpaas.servicebroker.model.CreateServiceInstanceBindingRequest;
import org.openpaas.servicebroker.model.CreateServiceInstanceRequest;
import org.openpaas.servicebroker.model.CreateServiceInstanceResponse;
import org.openpaas.servicebroker.model.Plan;
import org.openpaas.servicebroker.model.ServiceDefinition;
import org.openpaas.servicebroker.model.ServiceInstance;
import org.openpaas.servicebroker.model.ServiceInstanceBinding;
import org.openpaas.servicebroker.model.ServiceInstanceBindingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper; 

@Controller 
@EnableAutoConfiguration 
public class MainController {
	
	
	public String encryptSHA_1(String input) throws NoSuchAlgorithmException {
        MessageDigest mDigest = MessageDigest.getInstance("SHA1");
        byte[] result = mDigest.digest(input.getBytes());
 
        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i < result.length; i++) {
            stringBuffer.append(Integer.toString((result[i] & 0xff) + 0x100, 16).substring(1));
        }
        
        return stringBuffer.toString();
    }

	
//	Logger logger = Logger.getLogger(SampleController.class.getName());
	private static final Logger logger = LoggerFactory.getLogger(MainController.class);
	@RequestMapping("/") 
	@ResponseBody 
	String home() 
	{ 
		return "Hello World!"; 
	} 
	
	Catalog m_cat = null;
	List<ServiceInstance> m_services = new ArrayList<ServiceInstance>();
	List<ServiceInstanceBinding> m_bindings = new ArrayList<ServiceInstanceBinding>();
		
	/*
	 * catalog list handler
	 */
	
	@RequestMapping(value="/v2/catalog", method=RequestMethod.GET) 
	@ResponseBody 
	Catalog cataloglist(@RequestHeader("X-Broker-API-Version") String apiver, HttpServletRequest httpreq) 
	{
		System.out.printf("cataloglist(apiversion=%s)\n", apiver);
		
		if(m_cat!=null) return m_cat;
		
		
//		System.out.printf("url=[%s]", httpreq.getRequestURL());
		
		HashMap<String,Object> meta = new HashMap<String,Object>();
		HashMap<String,Object> planmeta = new HashMap<String,Object>();
		
		// ankus service broker 정보
		
		meta.put("longDescription", "ankus Service"); 
		meta.put("documentationUrl", "http://web1.openankus.com/upload/ankus4paasapi.html");
		meta.put("providerDisplayName", "ankus"); 
		meta.put("displayName", "ankus"); 
		meta.put("imageUrl", "http://web1.openankus.com/images/main/logo_ankus.png"); 
		meta.put("supportUrl", "http://web1.openankus.com");
		
		// ankus service broker 의 plan 정보 초기 Free

		Plan plan =  new Plan(
				"17113748-5e43-4168-8bb9-06a7925f97ba", "ankus-plan", "ankus", 
				planmeta,true); 

		m_cat = new Catalog( 
		Arrays.asList(
		new ServiceDefinition("35d4680f-61fd-4f2f-8dad-fd4f7802c6fe", 
				"ankus-service", 
				"ankus service broker", 
				true,  // bindable
				false, // plan updatable 
				Arrays.asList(plan), 
				Arrays.asList("ankus", "bigdata"), 
				meta, 
				Arrays.asList("syslog_drain"), 
				null)
		)
		);
		
		return m_cat;
	}

	/*
	 * create service broker handler
	 */
	
	@RequestMapping(value = {"/v2/service_instances/{instanceId}"}, method = {RequestMethod.PUT},
			headers = {"Content-Type=application/json"} ) 
	public ResponseEntity<CreateServiceInstanceResponse> createServiceInstance(@RequestHeader("X-Broker-API-Version") String apiver, @PathVariable("instanceId") String serviceInstanceId, @Valid @RequestBody CreateServiceInstanceRequest request) throws ServiceDefinitionDoesNotExistException, ServiceInstanceExistsException, ServiceBrokerException 
	{
		if(m_cat==null) cataloglist(apiver, null);
		
		System.out.printf("createServiceInstance(apiversion=%s,serviceInstanceId=[%s])\n", apiver, serviceInstanceId);
		
		ServiceDefinition svc = null;
		for(ServiceDefinition s : m_cat.getServiceDefinitions()) 
		{
			if(s.getId().equals(request.getServiceDefinitionId()))
			{
				svc = s; 
				break;
			}
		}
		if(svc == null) {
			throw new ServiceDefinitionDoesNotExistException(request.getServiceDefinitionId());
		} else {
			
			logger.debug("ServiceDefinitionDoesNotExistException");
			
			Plan plan = null;
			
			for(Plan p : svc.getPlans()) if(p.getId().equals(request.getPlanId())) { plan =  p; break;}
			
			if(plan==null)
			{
				logger.error("Invalid PlanID : ["+request.getPlanId()+"]");
				throw new ServiceBrokerException("Invalid PlanID : ["+request.getPlanId()+"]");					
			}
			
			ServiceInstance instance = new ServiceInstance(request);
			
			instance.setServiceInstanceId(serviceInstanceId);
			m_services.add(instance);// 추가...
			
			ObjectMapper mapper = new ObjectMapper();
			
			String s = "";
			try {
				s = mapper.writeValueAsString(instance);
			} catch (JsonProcessingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			System.out.printf("m_services=[%d:%s:%s]\n", m_services.size(), instance.getServiceInstanceId(), s);
			
//			ServiceInstance instance = this.service.createServiceInstance(request.withServiceDefinition(svc).and().withServiceInstanceId(serviceInstanceId));
			logger.debug("ServiceInstance Created: " + instance.getServiceInstanceId());
			return new ResponseEntity(new CreateServiceInstanceResponse(instance), instance.getHttpStatus());     
		} 
	} 
	
	/* 사용안함...
	 * 
	 * 
	 * update service broker handler
	 * 
	 * 
	 * 
	@RequestMapping(value = {"/v2/service_instances/{instanceId}"}, method = {RequestMethod.PATCH} )
	public ResponseEntity<String> updateServiceInstance(@PathVariable("instanceId") String instanceId, @Valid @RequestBody UpdateServiceInstanceRequest request) throws ServiceInstanceUpdateNotSupportedException, ServiceInstanceDoesNotExistException, ServiceBrokerException 
	{          
		ServiceInstance instance = this.service.updateServiceInstance(request.withInstanceId(instanceId));
		logger.debug("ServiceInstance updated: " + instance.getServiceInstanceId ());
		return new ResponseEntity("{}", HttpStatus.OK); 
	} 
	*/

	
	/*
	 * bind service broker handler
	 */
	@RequestMapping(value = {"/v2/service_instances/{instanceId}/service_bindings/{bindingId}"}, method = {RequestMethod.PUT},
			headers = {"Content-Type=application/json"} )
	public ResponseEntity<ServiceInstanceBindingResponse> bindServiceInstance(@RequestHeader("X-Broker-API-Version") String apiver, @PathVariable("instanceId") String instanceId, @PathVariable("bindingId") String bindingId, @Valid @RequestBody CreateServiceInstanceBindingRequest request, HttpServletRequest httpreq) throws ServiceInstanceDoesNotExistException, ServiceInstanceBindingExistsException, ServiceBrokerException 
	{
		System.out.printf("bindServiceInstance(apiversion=%s,serviceInstanceId=[%s], bindingId=[%s], m_services=%s)\n", apiver, instanceId, bindingId, m_services);
		
//		String instanceId = request.getServiceInstanceId();
		String serviceId = request.getServiceDefinitionId();
		String planId = request.getPlanId();
//		String bindingId = request.getBindingId();
		String appGuid = request.getAppGuid();
		//credential 값을 넣는다.
		HashMap<String,Object> credentials = new HashMap<String, Object>();
		
		// key발급 및 REST URL, document URL 생성..
		credentials.put("apikey", "admin");
		credentials.put("baseurl", "http://vpn.xip.kr/");
		credentials.put("ankus analyzer", "http://vpn.xip.kr/");
		credentials.put("documentUrl", "http://web1.openankus.com/upload/ankus4paasapi.html");
		
		ServiceInstance instance = null;
		for(ServiceInstance si:m_services)
		{
			if(si.getServiceInstanceId().equals(instanceId))
			{
				instance = si; 
				break;
			}
		}
		
		if(instance == null) {         
			throw new ServiceInstanceDoesNotExistException(instanceId);
		} else {         
			ServiceInstanceBinding binding = new ServiceInstanceBinding(bindingId, instanceId, credentials, null, appGuid);
			
			ObjectMapper mapper = new ObjectMapper();
			
			String s = "";
			try {
				s = mapper.writeValueAsString(binding);
			} catch (JsonProcessingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			System.out.printf("binding=[%d:%s:%s]\n", m_bindings.size(), instance.getServiceInstanceId(), s);
			
			m_bindings.add(binding); // 추가..
			return new ResponseEntity(new ServiceInstanceBindingResponse(binding), binding.getHttpStatus());     
		} 
	}

	/*
	 * unbind service broker handler
	 */
	
	@RequestMapping(value = {"/v2/service_instances/{instanceId}/service_bindings/{bindingId}"}, method = {RequestMethod.DELETE}
//	,headers = {"Content-Type=application/json"} 
	) 
	public ResponseEntity<String> deleteServiceInstanceBinding(@RequestHeader("X-Broker-API-Version") String apiver, @PathVariable("instanceId") String instanceId, @PathVariable("bindingId") String bindingId, @RequestParam("service_id") String serviceId, @RequestParam("plan_id") String planId) throws ServiceBrokerException, ServiceInstanceDoesNotExistException 
	{
		System.out.printf("deleteServiceInstanceBinding(apiversion=%s, instanceId=[%s], bindingId=[%s], serviceId=[%s], planId=[%s])\n", apiver, instanceId, bindingId, serviceId, planId);
		
		ServiceInstanceBinding binding = null;
		int binding_idx = -1;
		for(int i=0; i<m_bindings.size(); i++)
		{
			ServiceInstanceBinding b = m_bindings.get(i);
			if(b.getServiceInstanceId().equals(instanceId) && b.getId().equals(bindingId)) 
			{
				binding = b; 
				binding_idx = i; 
				break;
			}
		}
//		ServiceInstance instance = this.serviceInstanceService.getServiceInstance(instanceId);
		if(binding == null) { // binding 정보없음...
			return new ResponseEntity("{}", HttpStatus.GONE);
//			throw new ServiceInstanceDoesNotExistException(instanceId);
		} else {
			ServiceInstanceBinding del_binding = m_bindings.remove(binding_idx);
			if(del_binding == null) {
				return new ResponseEntity("{}", HttpStatus.GONE);
			} else {
				logger.debug("ServiceInstanceBinding Deleted: " + binding.getId());
				return new ResponseEntity("{}", HttpStatus.OK);
			}
		} 
	} 

	/*
	 * delete service broker handler
	 */
	
	@RequestMapping(value = {"/v2/service_instances/{instanceId}"}, method = {RequestMethod.DELETE}
//	,headers = {"Content-Type=application/json"} 
	) 
	public ResponseEntity<String> deleteServiceInstance(@RequestHeader("X-Broker-API-Version") String apiver, @PathVariable("instanceId" ) String instanceId, @RequestParam("service_id") String serviceId, @RequestParam("plan_id") String planId) throws ServiceBrokerException 
	{
		System.out.printf("deleteServiceInstance(apiversion=%s, instanceId=[%s], serviceId=[%s], planId=[%s])\n", apiver, instanceId, serviceId, planId);
		
		
		ServiceInstance instance = null;
		int instance_idx = -1;
		for(int i=0; i<m_services.size(); i++)
		{
			ServiceInstance si = m_services.get(i);
			if(si.getServiceInstanceId().equals(instanceId)) 
			{
				instance = si; 
				instance_idx = i; 
				break;
			}
		}
		logger.debug("DELETE: /v2/service_instances/{instanceId}, deleteServiceInstanc eBinding(), serviceInstanceId = " + instanceId + ", serviceId = " + serviceId + ", planId = " + planId);
		if(instance == null) {         
			return new ResponseEntity("{}", HttpStatus.GONE);
		} else {
			
			m_services.remove(instance_idx); // 삭제..
											 // binding 삭제 ?
			logger.debug("ServiceInstance Deleted: " + instance.getServiceInstanceId()) ;         
			return new ResponseEntity("{}", HttpStatus.OK);     
		} 
	} 
	
}

