/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Arbalo AG
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.opentdc.wtt.opencrx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.jdo.PersistenceManager;
import javax.naming.NamingException;
import javax.servlet.ServletContext;

import org.opencrx.kernel.account1.cci2.LegalEntityQuery;
import org.opencrx.kernel.account1.jmi1.LegalEntity;
import org.opencrx.kernel.activity1.cci2.ActivityQuery;
import org.opencrx.kernel.activity1.cci2.ResourceAssignmentQuery;
import org.opencrx.kernel.activity1.jmi1.Activity;
import org.opencrx.kernel.activity1.jmi1.ActivityTracker;
import org.opencrx.kernel.activity1.jmi1.Resource;
import org.opencrx.kernel.activity1.jmi1.ResourceAssignment;
import org.opencrx.kernel.utils.Utils;
import org.openmdx.base.exception.ServiceException;
import org.opentdc.opencrx.AbstractOpencrxServiceProvider;
import org.opentdc.opencrx.ActivitiesHelper;
import org.opentdc.service.exception.DuplicateException;
import org.opentdc.service.exception.InternalServerErrorException;
import org.opentdc.service.exception.NotFoundException;
import org.opentdc.wtt.CompanyModel;
import org.opentdc.wtt.ProjectModel;
import org.opentdc.wtt.ResourceModel;
import org.opentdc.wtt.ServiceProvider;

public class OpencrxServiceProvider extends AbstractOpencrxServiceProvider implements ServiceProvider {
	
	// instance variables
	private static final Logger logger = Logger.getLogger(OpencrxServiceProvider.class.getName());
	
	public OpencrxServiceProvider(
		ServletContext context,
		String prefix
	) throws ServiceException, NamingException {
		super(context, prefix);
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#listCompanies(boolean, java.lang.String, java.lang.String, long, long)
	 */
	@Override
	public List<CompanyModel> listCompanies(
		boolean asTree,
		String query, 
		String queryType, 
		int position, 
		int size
	) {
		logger.info("listCompanies() -> " + countCompanies() + " companies");
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		List<ActivityTracker> trackers = ActivitiesHelper.getCustomerProjectGroups(activitySegment, null);
		List<CompanyModel> companies = new ArrayList<CompanyModel>();
		for(ActivityTracker tracker : trackers) {
			companies.add(
				new CompanyModel(
					tracker.refGetPath().getLastSegment().toClassicRepresentation(),
					tracker.refGetPath().toXRI(), 
					tracker.getName(), 
					tracker.getDescription()
				)
			);
		}
		Collections.sort(companies, CompanyModel.CompanyComparator);
		return companies;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#createCompany(org.opentdc.wtt.CompanyModel)
	 */
	@Override
	public CompanyModel createCompany(
		CompanyModel company
	)  throws DuplicateException {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		org.opencrx.kernel.account1.jmi1.Segment accountSegment = this.getAccountSegment();
		if(company.getId() != null) {
			try {
				readCompany(company.getId());
				throw new DuplicateException("Company with ID " + company.getId() + " exists already.");
			} catch(NotFoundException ignore) {}
		}
		LegalEntity customer = null;
		{
			LegalEntityQuery customerQuery = (LegalEntityQuery)pm.newQuery(LegalEntity.class);
			customerQuery.name().equalTo(company.getTitle());
			customerQuery.forAllDisabled().isFalse();
			List<LegalEntity> customers = accountSegment.getAccount(customerQuery);
			if(customers.isEmpty()) {
				customer = pm.newInstance(LegalEntity.class);
				customer.setName(company.getTitle());
				try {
					pm.currentTransaction().begin();
					accountSegment.addAccount(
						Utils.getUidAsString(),
						customer
					);
					pm.currentTransaction().commit();
				} catch(Exception e) {
					new ServiceException(e).log();
					try {
						pm.currentTransaction().rollback();
					} catch(Exception ignore) {}
					throw new InternalServerErrorException(e.getMessage());
				}
			} else {
				customer = customers.iterator().next();
			}
		}
		ActivityTracker customerProjectGroup = ActivitiesHelper.createCustomerProjectGroup(
			pm,
			activitySegment,
			company.getTitle(), 
			company.getDescription(),
			customer
		);
		if(customerProjectGroup == null) {
			throw new InternalServerErrorException();
		} else {
			CompanyModel newCompany = new CompanyModel(
				customerProjectGroup.refGetPath().getLastSegment().toClassicRepresentation(),
				customerProjectGroup.refGetPath().toXRI(),
				customerProjectGroup.getName(),
				customerProjectGroup.getDescription()
			);
			logger.info("createCompany() -> " + newCompany);
			return newCompany;
		}
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#readCompany(java.lang.String)
	 */
	@Override
	public CompanyModel readCompany(
		String id
	)  throws NotFoundException {
		CompanyModel company = null;
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		ActivityTracker customerProjectGroup = null;
		try {
			customerProjectGroup = activitySegment.getActivityTracker(id);
		} catch(Exception ignore) {}
		if(customerProjectGroup == null || Boolean.TRUE.equals(customerProjectGroup.isDisabled())) {
			throw new NotFoundException("no company with ID <" + id + "> found.");
		}
		company = new CompanyModel(
			customerProjectGroup.refGetPath().getLastSegment().toClassicRepresentation(),
			customerProjectGroup.refGetPath().toXRI(),
			customerProjectGroup.getName(),
			customerProjectGroup.getDescription()
		);
		logger.info("readCompany(" + id + ") -> " + company);
		return company;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#updateCompany(java.lang.String, org.opentdc.wtt.CompanyModel)
	 */
	@Override
	public CompanyModel updateCompany(
		String id,
		CompanyModel company
	) throws NotFoundException {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		ActivityTracker customerProjectGroup = null;
		try {
			customerProjectGroup = activitySegment.getActivityTracker(id);
		} catch(Exception ignore) {}
		if(customerProjectGroup == null || Boolean.TRUE.equals(customerProjectGroup.isDisabled())) {
			throw new NotFoundException("no company with ID <" + id + "> found.");
		}
		try {
			pm.currentTransaction().begin();
			customerProjectGroup.setName(company.getTitle());
			customerProjectGroup.setDescription(company.getDescription());
			pm.currentTransaction().commit();
		} catch(Exception e) {
			new ServiceException(e).log();
			try {
				pm.currentTransaction().rollback();
			} catch(Exception ignore) {}
			throw new InternalServerErrorException(e.getMessage());
		}
		company = new CompanyModel(
			customerProjectGroup.refGetPath().getLastSegment().toClassicRepresentation(),
			customerProjectGroup.refGetPath().toXRI(),
			customerProjectGroup.getName(),
			customerProjectGroup.getDescription()
		);
		logger.info("updateCompany(" + id + ") -> " + company);
		return company;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#deleteCompany(java.lang.String)
	 */
	@Override
	public void deleteCompany(
		String id
	) throws NotFoundException {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		ActivityTracker customerProjectGroup = null;
		try {
			customerProjectGroup = activitySegment.getActivityTracker(id);
		} catch(Exception ignore) {}
		if(customerProjectGroup == null || Boolean.TRUE.equals(customerProjectGroup.isDisabled())) {
			throw new NotFoundException("no company with ID <" + id + "> found.");
		}
		// Disable tracker and assigned activities
		try {
			pm.currentTransaction().begin();
			customerProjectGroup.setDisabled(true);
			List<Activity> customerProjects = ActivitiesHelper.getCustomerProjects(customerProjectGroup, false);
			for(Activity project: customerProjects) {
				project.setDisabled(true);
			}
			pm.currentTransaction().commit();
		} catch(Exception e) {
			new ServiceException(e).log();
			try {
				pm.currentTransaction().rollback();
			} catch(Exception ignore) {}
			throw new InternalServerErrorException(e.getMessage());
		}
		logger.info("deleteCompany(" + id + ")");
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#countCompanies()
	 */
	@Override
	public int countCompanies(
	) {
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		return ActivitiesHelper.getCustomerProjectGroups(
			activitySegment, 
			null
		).size();
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#listProjects(java.lang.String, java.lang.String, java.lang.String, int, int)
	 */
	@Override
	public List<ProjectModel> listProjects(
		String compId,
		String query, 
		String queryType, 
		int position, 
		int size
	) {
		return this.listAllProjects(
			compId, 
			true, // asTree 
			query, 
			queryType, 
			position, 
			size
		);
	}

	protected List<ProjectModel> getSubprojects(
		String projectId
	) {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		Activity project = activitySegment.getActivity(projectId);
		ActivityQuery subprojectsQuery = (ActivityQuery)pm.newQuery(Activity.class);
		subprojectsQuery.thereExistsActivityLinkTo().activityLinkType().equalTo(ActivitiesHelper.ACTIVITY_LINK_TYPE_IS_CHILD_OF);
		subprojectsQuery.thereExistsActivityLinkTo().thereExistsLinkTo().equalTo(project);
		List<Activity> subprojects = activitySegment.getActivity(subprojectsQuery);
		List<ProjectModel> result = new ArrayList<ProjectModel>();
		for(Activity subproject: subprojects) {
			ProjectModel p = new ProjectModel(
				subproject.refGetPath().getLastSegment().toClassicRepresentation(),
				subproject.refGetPath().toXRI(), 
				subproject.getName(), 
				subproject.getDescription()
			);
			p.setProjects(
				this.getSubprojects(p.getId())
			);
			result.add(p);
		}
		return result;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#listAllProjects(java.lang.String, boolean, java.lang.String, java.lang.String, long, long)
	 */
	@Override
	public List<ProjectModel> listAllProjects(
		String compId, 
		boolean asTree,
		String query, 
		String queryType, 
		int position, 
		int size
	) {
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		ActivityTracker customerProjectGroup = null;
		try {
			customerProjectGroup = activitySegment.getActivityTracker(compId);
		} catch(Exception ignore) {}
		if(customerProjectGroup == null || Boolean.TRUE.equals(customerProjectGroup.isDisabled())) {
			throw new NotFoundException("no company with ID <" + compId + "> found.");
		}
		List<Activity> customerProjects = ActivitiesHelper.getCustomerProjects(customerProjectGroup, asTree);
		ArrayList<ProjectModel> result = new ArrayList<ProjectModel>();
		for (Activity customerProject: customerProjects) {
			ProjectModel project = new ProjectModel(
				customerProject.refGetPath().getLastSegment().toClassicRepresentation(),
				customerProject.refGetPath().toXRI(), 
				customerProject.getName(), 
				customerProject.getDescription()
			);
			if(asTree) {
				project.setProjects(
					this.getSubprojects(project.getId())
				);
			}
			result.add(project);
		}
		return result;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#createProject(java.lang.String, org.opentdc.wtt.ProjectModel)
	 */
	@Override
	public ProjectModel createProject(
		String compId, 
		ProjectModel newProject
	) throws DuplicateException {
		logger.info("> createProject(" + compId + ", " + newProject + ")");
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		ActivityTracker customerProjectGroup = null;
		try {
			customerProjectGroup = activitySegment.getActivityTracker(compId);
		} catch(Exception ignore) {}
		if(customerProjectGroup == null || Boolean.TRUE.equals(customerProjectGroup.isDisabled())) {
			throw new NotFoundException("no company with ID <" + compId + "> found.");
		}
		if(newProject.getId() != null) {
			Activity project = null;
			try {
				project = activitySegment.getActivity(newProject.getId());
			} catch(Exception ignore) {}
			if(project != null) {
				throw new DuplicateException("Project with ID " + newProject.getId() + " exists already.");				
			}
		}
		Activity project = ActivitiesHelper.createCustomerProject(
			pm,
			customerProjectGroup,
			newProject.getTitle(), 
			newProject.getDescription(), 
			null,
			new Date(), 
			new Date(),
			ActivitiesHelper.ACTIVITY_PRIORITY_NA,
			null
		);
		if(project == null) {
			throw new InternalServerErrorException();
		} else {
			return new ProjectModel(				
				project.refGetPath().getLastSegment().toClassicRepresentation(),
				project.refGetPath().toXRI(),
				newProject.getTitle(),
				newProject.getDescription()
			);
		}
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#createProjectAsSubproject(java.lang.String, java.lang.String, org.opentdc.wtt.ProjectModel)
	 */
	@Override
	public ProjectModel createProjectAsSubproject(
		String compId, 
		String projId, 
		ProjectModel newProject
	) throws DuplicateException {
		logger.info("> createProjectAsSubproject(" + compId + ", " + newProject + ")");
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		ActivityTracker customerProjectGroup = null;
		try {
			customerProjectGroup = activitySegment.getActivityTracker(compId);
		} catch(Exception ignore) {}
		if(customerProjectGroup == null || Boolean.TRUE.equals(customerProjectGroup.isDisabled())) {
			throw new NotFoundException("no company with ID <" + compId + "> found.");
		}
		Activity parentProject = null;
		try {
			parentProject = activitySegment.getActivity(projId);
		} catch(Exception ignore) {}
		if(parentProject == null) {
			throw new NotFoundException("Project with ID " + projId + " not found.");				
		}
		if(newProject.getId() != null) {
			Activity project = null;
			try {
				project = activitySegment.getActivity(newProject.getId());
			} catch(Exception ignore) {}
			if(project != null) {
				throw new DuplicateException("Project with ID " + newProject.getId() + " exists already.");				
			}
		}
		Activity project = ActivitiesHelper.createCustomerProject(
			pm,
			customerProjectGroup,
			newProject.getTitle(), 
			newProject.getDescription(), 
			null,
			new Date(), 
			new Date(), 
			ActivitiesHelper.ACTIVITY_PRIORITY_NA,
			parentProject
		);
		if(project == null) {
			throw new InternalServerErrorException();
		} else {
			return new ProjectModel(				
				project.refGetPath().getLastSegment().toClassicRepresentation(),
				project.refGetPath().toXRI(),
				newProject.getTitle(),
				newProject.getDescription()
			);
		}
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#readProject(java.lang.String)
	 */
	@Override
	public ProjectModel readProject(
		String projId
	) throws NotFoundException {
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();		
		Activity project = null;
		try {
			project = activitySegment.getActivity(projId);
		} catch(Exception ignore) {}
		if(project == null || Boolean.TRUE.equals(project.isDisabled())) {
			throw new NotFoundException("no project with ID <" + projId + "> found.");
		}
		ProjectModel p = new ProjectModel(
			project.refGetPath().getLastSegment().toClassicRepresentation(),
			project.refGetPath().toXRI(),
			project.getName(),
			project.getDescription()
		);
		p.setProjects(
			this.getSubprojects(p.getId())
		);
		logger.info("readProject(" + projId + "): " + p);
		return p;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#updateProject(java.lang.String, java.lang.String, org.opentdc.wtt.ProjectModel)
	 */
	@Override
	public ProjectModel updateProject(
		String compId,
		String projId,
		ProjectModel p
	) throws NotFoundException {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();		
		Activity project = null;
		try {
			project = activitySegment.getActivity(projId);
		} catch(Exception ignore) {}
		if(project == null) {
			throw new NotFoundException("no project with ID <" + projId + "> found.");
		}
		try {
			pm.currentTransaction().begin();
			project.setName(p.getTitle());
			project.setDescription(p.getDescription());
			pm.currentTransaction().commit();
		} catch(Exception e) {
			new ServiceException(e).log();
			try {
				pm.currentTransaction().rollback();
			} catch(Exception ignore) {}
		}
		return p;
	}

	protected void deleteSubprojects(
		String projectId
	) {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();		
		List<ProjectModel> subprojects = this.getSubprojects(projectId);
		for(ProjectModel subproject: subprojects) {
			try {
				pm.currentTransaction().begin();
				Activity p = activitySegment.getActivity(subproject.getId());
				p.setDisabled(true);
				pm.currentTransaction().commit();
			} catch(Exception e) {
				new ServiceException(e).log();
				try {
					pm.currentTransaction().rollback();
				} catch(Exception ignore) {}
			}
			this.deleteSubprojects(subproject.getId());
		}
	}
	
	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#deleteProject(java.lang.String, java.lang.String)
	 */
	@Override
	public void deleteProject(
		String compId, 
		String projId
	) throws NotFoundException {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();		
		Activity project = null;
		try {
			project = activitySegment.getActivity(projId);
		} catch(Exception ignore) {}
		if(project == null || Boolean.TRUE.equals(project.isDisabled())) {
			throw new NotFoundException("no project with ID <" + projId + "> found.");
		}
		try {
			pm.currentTransaction().begin();
			project.setDisabled(true);
			pm.currentTransaction().commit();
			this.deleteSubprojects(projId);
		} catch(Exception e) {
			new ServiceException(e).log();
			try {
				pm.currentTransaction().rollback();
			} catch(Exception ignore) {}
		}
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#countProjects(java.lang.String)
	 */
	@Override
	public int countProjects(
		String compId
	) {
		return this.listAllProjects(
			compId, 
			true, // asTree
			null, // query
			null, // queryType
			0, // position
			Integer.MAX_VALUE
		).size();
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#listResources(java.lang.String, java.lang.String, java.lang.String, long, long)
	 */
	@Override
	public List<ResourceModel> listResources(
		String projId,
		String query, 
		String queryType, 
		int position,
		int size
	)  throws NotFoundException {
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();		
		Activity project = null;
		try {
			project = activitySegment.getActivity(projId);
		} catch(Exception ignore) {}
		if(project == null || Boolean.TRUE.equals(project.isDisabled())) {
			throw new NotFoundException("no project with ID <" + projId + "> found.");
		}
		List<Resource> resources = ActivitiesHelper.getProjectResources(project);
		List<ResourceModel> result = new ArrayList<ResourceModel>();
		for(Resource resource: resources) {
			result.add(
				new ResourceModel(resource.refGetPath().getLastSegment().toClassicRepresentation())
			);
		}
		return result;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#addResource(java.lang.String, java.lang.String)
	 */
	@Override
	public String addResource(
		String projId, 
		String resourceId
	) throws NotFoundException {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();		
		Activity project = null;
		try {
			project = activitySegment.getActivity(projId);
		} catch(Exception ignore) {}
		if(project == null || Boolean.TRUE.equals(project.isDisabled())) {
			throw new NotFoundException("no project with ID <" + projId + "> found.");
		}
		Resource resource = null;
		try {
			resource = activitySegment.getResource(resourceId);
		} catch(Exception ignore) {}
		if(resource == null || Boolean.TRUE.equals(resource.isDisabled())) {
			throw new NotFoundException("no resource with ID <" + resourceId + "> found.");
		}
		try {
			ResourceAssignment resourceAssignment = pm.newInstance(ResourceAssignment.class);
			pm.currentTransaction().begin();
			resourceAssignment.setName(resource.getName());
			resourceAssignment.setResource(resource);
			resourceAssignment.setResourceRole(ActivitiesHelper.RESOURCE_ROLE_MEMBER);
			project.addAssignedResource(
				Utils.getUidAsString(),
				resourceAssignment
			);
			pm.currentTransaction().commit();
		} catch(Exception e) {
			new ServiceException(e).log();
			try {
				pm.currentTransaction().rollback();
			} catch(Exception ignore) {}
			throw new InternalServerErrorException();
		}
		return resourceId;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#removeResource(java.lang.String, java.lang.String)
	 */
	@Override
	public void removeResource(
		String projId, 
		String resourceId
	) throws NotFoundException {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();		
		Activity project = null;
		try {
			project = activitySegment.getActivity(projId);
		} catch(Exception ignore) {}
		if(project == null || Boolean.TRUE.equals(project.isDisabled())) {
			throw new NotFoundException("no project with ID <" + projId + "> found.");
		}
		Resource resource = null;
		try {
			resource = activitySegment.getResource(resourceId);
		} catch(Exception ignore) {}
		if(resource == null || Boolean.TRUE.equals(resource.isDisabled())) {
			throw new NotFoundException("no resource with ID <" + resourceId + "> found.");
		}
		ResourceAssignmentQuery resourceAssignmentQuery = (ResourceAssignmentQuery)pm.newQuery(ResourceAssignment.class);
		resourceAssignmentQuery.thereExistsResource().equalTo(resource);
		List<ResourceAssignment> resourceAssignments = project.getAssignedResource(resourceAssignmentQuery);
		try {
			pm.currentTransaction().begin();
			for(ResourceAssignment resourceAssignment: resourceAssignments) {
				resourceAssignment.setDisabled(true);
			}
			pm.currentTransaction().commit();
		} catch(Exception e) {
			new ServiceException(e).log();
			try {
				pm.currentTransaction().rollback();
			} catch(Exception ignore) {}
			throw new InternalServerErrorException();
		}
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#countResources(java.lang.String)
	 */
	@Override
	public int countResources(
		String projId
	) throws NotFoundException {
		return this.listResources(
			projId, 
			null, // query
			null, // queryType
			0, // position
			Integer.MAX_VALUE
		).size();
	}
	
}
