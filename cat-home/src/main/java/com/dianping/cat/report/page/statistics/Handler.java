package com.dianping.cat.report.page.statistics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;

import org.unidal.dal.jdbc.DalException;
import org.unidal.lookup.annotation.Inject;
import org.unidal.tuple.Pair;
import org.unidal.web.mvc.PageHandler;
import org.unidal.web.mvc.annotation.InboundActionMeta;
import org.unidal.web.mvc.annotation.OutboundActionMeta;
import org.unidal.web.mvc.annotation.PayloadMeta;

import com.dianping.cat.Cat;
import com.dianping.cat.Constants;
import com.dianping.cat.core.dal.Project;
import com.dianping.cat.core.dal.ProjectDao;
import com.dianping.cat.core.dal.ProjectEntity;
import com.dianping.cat.helper.MapUtils;
import com.dianping.cat.helper.TimeUtil;
import com.dianping.cat.home.alertReport.entity.AlertReport;
import com.dianping.cat.home.alertReport.entity.Exception;
import com.dianping.cat.home.bug.entity.BugReport;
import com.dianping.cat.home.bug.entity.Domain;
import com.dianping.cat.home.bug.entity.ExceptionItem;
import com.dianping.cat.home.bug.transform.BaseVisitor;
import com.dianping.cat.home.heavy.entity.HeavyCache;
import com.dianping.cat.home.heavy.entity.HeavyCall;
import com.dianping.cat.home.heavy.entity.HeavyReport;
import com.dianping.cat.home.heavy.entity.HeavySql;
import com.dianping.cat.home.heavy.entity.Service;
import com.dianping.cat.home.heavy.entity.Url;
import com.dianping.cat.home.service.entity.ServiceReport;
import com.dianping.cat.home.utilization.entity.UtilizationReport;
import com.dianping.cat.report.ReportPage;
import com.dianping.cat.report.page.PayloadNormalizer;
import com.dianping.cat.report.service.ReportService;
import com.dianping.cat.report.task.heavy.HeavyReportMerger.ServiceComparator;
import com.dianping.cat.report.task.heavy.HeavyReportMerger.UrlComparator;
import com.dianping.cat.system.config.BugConfigManager;

public class Handler implements PageHandler<Context> {
	@Inject
	private JspViewer m_jspViewer;

	@Inject
	private ReportService m_reportService;

	@Inject
	private ProjectDao m_projectDao;

	@Inject
	private BugConfigManager m_bugConfigManager;

	@Inject
	private PayloadNormalizer m_normalizePayload;

	private void buildBugInfo(Model model, Payload payload) {
		BugReport bugReport = queryBugReport(payload);
		BugReportVisitor visitor = new BugReportVisitor();
		visitor.visitBugReport(bugReport);

		Map<String, ErrorStatis> errors = visitor.getErrors();
		errors = sortErrorStatis(errors);
		model.setErrorStatis(errors);

		if (payload.getAction() == Action.BUG_HTTP_JSON) {
			new ClearBugReport().visitBugReport(bugReport);
		}
		model.setBugReport(bugReport);
	}

	private void buildAlertInfo(Model model, Payload payload) {
		AlertReport alertReport = queryAlertReport(payload);
		model.setAlertReport(alertReport);
		List<com.dianping.cat.home.alertReport.entity.Domain> sortedDoamins = buildSortedAlertInfo(alertReport, model);
		model.setAlertDomains(sortedDoamins);
	}
	
	private void builAlertDetails(Model model, Payload payload) {
		AlertReport alertReport = queryAlertReport(payload);
		List<com.dianping.cat.home.alertReport.entity.Exception> sortedExceptions = buildSortedAlertDetails(alertReport,
		      payload.getDomain());

		model.setAlertExceptions(sortedExceptions);
	}
	
	private void buildHeavyInfo(Model model, Payload payload) {
		HeavyReport heavyReport = queryHeavyReport(payload);

		model.setHeavyReport(heavyReport);
		buildSortedHeavyInfo(model, heavyReport);
	}

	private void buildServiceInfo(Model model, Payload payload) {
		ServiceReport serviceReport = queryServiceReport(payload);
		List<com.dianping.cat.home.service.entity.Domain> dHisList = sort(serviceReport, payload.getSortBy());
		model.setServiceList(dHisList);
		model.setServiceReport(serviceReport);
	}

	private void buildSortedHeavyInfo(Model model, HeavyReport heavyReport) {
		HeavyCall heavyCall = heavyReport.getHeavyCall();
		if (heavyCall != null) {

			List<Url> callUrls = new ArrayList<Url>(heavyCall.getUrls().values());
			List<Service> callServices = new ArrayList<Service>(heavyCall.getServices().values());
			Collections.sort(callUrls, new UrlComparator());
			Collections.sort(callServices, new ServiceComparator());
			model.setCallUrls(callUrls);
			model.setCallServices(callServices);
		}

		HeavySql heavySql = heavyReport.getHeavySql();

		if (heavySql != null) {
			List<Url> sqlUrls = new ArrayList<Url>(heavySql.getUrls().values());
			List<Service> sqlServices = new ArrayList<Service>(heavySql.getServices().values());
			Collections.sort(sqlUrls, new UrlComparator());
			Collections.sort(sqlServices, new ServiceComparator());
			model.setSqlUrls(sqlUrls);
			model.setSqlServices(sqlServices);
		}

		HeavyCache heavyCache = heavyReport.getHeavyCache();
		if (heavyCache != null) {
			List<Url> cacheUrls = new ArrayList<Url>(heavyCache.getUrls().values());
			List<Service> cacheServices = new ArrayList<Service>(heavyCache.getServices().values());
			Collections.sort(cacheUrls, new UrlComparator());
			Collections.sort(cacheServices, new ServiceComparator());
			model.setCacheUrls(cacheUrls);
			model.setCacheServices(cacheServices);
		}
	}
	
	private List<com.dianping.cat.home.alertReport.entity.Domain> buildSortedAlertInfo(AlertReport report, Model model) {
		List<com.dianping.cat.home.alertReport.entity.Domain> domains = new ArrayList<com.dianping.cat.home.alertReport.entity.Domain>(
		      report.getDomains().values());
		Comparator<com.dianping.cat.home.alertReport.entity.Domain> domainCompator = new Comparator<com.dianping.cat.home.alertReport.entity.Domain>() {

			@Override
			public int compare(com.dianping.cat.home.alertReport.entity.Domain o1,
			      com.dianping.cat.home.alertReport.entity.Domain o2) {
				int gap = o2.getErrorNumber() - o1.getErrorNumber();

				return gap == 0 ? o2.getWarnNumber() - o1.getWarnNumber() : gap;
			}
		};
		
		Collections.sort(domains, domainCompator);
		return domains;
	}
	
	private List<com.dianping.cat.home.alertReport.entity.Exception> buildSortedAlertDetails(AlertReport report,
	      String domainName) {
		com.dianping.cat.home.alertReport.entity.Domain domain = report.getDomains().get(domainName);
		List<com.dianping.cat.home.alertReport.entity.Exception> exceptions = new ArrayList<com.dianping.cat.home.alertReport.entity.Exception>(
		      domain.getExceptions().values());
		Comparator<com.dianping.cat.home.alertReport.entity.Exception> exceptionCompator = new Comparator<com.dianping.cat.home.alertReport.entity.Exception>() {

			@Override
			public int compare(Exception o1, Exception o2) {
				int gap = o2.getErrorNumber() - o1.getErrorNumber();

				return gap == 0 ? o2.getWarnNumber() - o1.getWarnNumber() : gap;
			}
		};

		Collections.sort(exceptions, exceptionCompator);
		return exceptions;
	}	
	private void buildUtilizationInfo(Model model, Payload payload) {
		UtilizationReport utilizationReport = queryUtilizationReport(payload);
		Collection<com.dianping.cat.home.utilization.entity.Domain> dUList = utilizationReport.getDomains().values();
		List<com.dianping.cat.home.utilization.entity.Domain> dUWebList = new LinkedList<com.dianping.cat.home.utilization.entity.Domain>();
		List<com.dianping.cat.home.utilization.entity.Domain> dUServiceList = new LinkedList<com.dianping.cat.home.utilization.entity.Domain>();
		for (com.dianping.cat.home.utilization.entity.Domain d : dUList) {
			if (d.findApplicationState("URL") != null) {
				dUWebList.add(d);
			}
			if (d.findApplicationState("PigeonService") != null) {
				dUServiceList.add(d);
			}
		}
		model.setUtilizationWebList(dUWebList);
		model.setUtilizationServiceList(dUServiceList);
		model.setUtilizationReport(utilizationReport);
	}

	public Project findProjectByDomain(String domain) {
		try {
			return m_projectDao.findByDomain(domain, ProjectEntity.READSET_FULL);
		} catch (DalException e) {
			Cat.logError(e);
		}
		return null;
	}

	@Override
	@PayloadMeta(Payload.class)
	@InboundActionMeta(name = "statistics")
	public void handleInbound(Context ctx) throws ServletException, IOException {
		// display only, no action here
	}

	@Override
	@OutboundActionMeta(name = "statistics")
	public void handleOutbound(Context ctx) throws ServletException, IOException {
		Model model = new Model(ctx);
		Payload payload = ctx.getPayload();
		m_normalizePayload.normalize(model, payload);
		Action action = payload.getAction();

		switch (action) {
		case SERVICE_REPORT:
		case SERVICE_HISTORY_REPORT:
			buildServiceInfo(model, payload);
			break;
		case BUG_HISTORY_REPORT:
		case BUG_REPORT:
		case BUG_HTTP_JSON:
			buildBugInfo(model, payload);
			break;
		case HEAVY_HISTORY_REPORT:
		case HEAVY_REPORT:
			buildHeavyInfo(model, payload);
			break;
		case UTILIZATION_REPORT:
		case UTILIZATION_HISTORY_REPORT:
			buildUtilizationInfo(model, payload);
			break;
		case ALERT_HISTORY_REPORT:
		case ALERT_REPORT:
			buildAlertInfo(model, payload);
			break;
		case ALERT_REPORT_DETAIL:
		case ALERT_HISTORY_REPORT_DETAIL:
			builAlertDetails(model, payload);
			break;
		}
		model.setPage(ReportPage.STATISTICS);
		m_jspViewer.view(ctx, model);
	}

	private boolean isBug(String domain, String exception) {
		Set<String> bugConfig = m_bugConfigManager.queryBugConfigsByDomain(domain);

		return !bugConfig.contains(exception);
	}

	private BugReport queryBugReport(Payload payload) {
		Pair<Date, Date> pair = queryStartEndTime(payload);

		return m_reportService.queryBugReport(Constants.CAT, pair.getKey(), pair.getValue());
	}

	private HeavyReport queryHeavyReport(Payload payload) {
		Pair<Date, Date> pair = queryStartEndTime(payload);

		return m_reportService.queryHeavyReport(Constants.CAT, pair.getKey(), pair.getValue());
	}
	
	private AlertReport queryAlertReport(Payload payload) {
		Pair<Date, Date> pair = queryStartEndTime(payload);
		
		return m_reportService.queryAlertReport(Constants.CAT, pair.getKey(), pair.getValue());
	}

	private ServiceReport queryServiceReport(Payload payload) {
		Pair<Date, Date> pair = queryStartEndTime(payload);

		return m_reportService.queryServiceReport(Constants.CAT, pair.getKey(), pair.getValue());
	}

	private Pair<Date, Date> queryStartEndTime(Payload payload) {
		Date start = null;
		Date end = null;
		Action action = payload.getAction();
		String name = action.getName();
		if ((!name.startsWith("history")) && (action != Action.BUG_HTTP_JSON)) {
			if (payload.getPeriod().isCurrent()) {
				start = new Date(payload.getDate() - TimeUtil.ONE_HOUR);
				end = new Date(start.getTime() + TimeUtil.ONE_HOUR);
			} else {
				start = new Date(payload.getDate());
				end = new Date(start.getTime() + TimeUtil.ONE_HOUR);
			}
		} else {
			start = payload.getHistoryStartDate();
			end = payload.getHistoryEndDate();
		}
		return new Pair<Date, Date>(start, end);
	}

	private UtilizationReport queryUtilizationReport(Payload payload) {
		Pair<Date, Date> pair = queryStartEndTime(payload);
		UtilizationReport report = m_reportService.queryUtilizationReport(Constants.CAT, pair.getKey(), pair.getValue());

		return report;
	}

	private List<com.dianping.cat.home.service.entity.Domain> sort(ServiceReport serviceReport, final String sortBy) {
		List<com.dianping.cat.home.service.entity.Domain> result = new ArrayList<com.dianping.cat.home.service.entity.Domain>(
		      serviceReport.getDomains().values());
		Collections.sort(result, new Comparator<com.dianping.cat.home.service.entity.Domain>() {
			public int compare(com.dianping.cat.home.service.entity.Domain d1,
			      com.dianping.cat.home.service.entity.Domain d2) {
				if (sortBy.equals("failure")) {
					return (int) (d2.getFailureCount() - d1.getFailureCount());
				} else if (sortBy.equals("total")) {
					long value = d2.getTotalCount() - d1.getTotalCount();

					if (value > 0) {
						return 1;
					} else {
						return -1;
					}
				} else if (sortBy.equals("failurePercent")) {
					return (int) (100000 * d2.getFailurePercent() - 100000 * d1.getFailurePercent());
				} else if (sortBy.equals("availability")) {
					return (int) (100000 * d2.getFailurePercent() - 100000 * d1.getFailurePercent());
				} else {
					return (int) (d2.getAvg() - d1.getAvg());
				}
			}
		});
		return result;
	}

	private Map<String, ErrorStatis> sortErrorStatis(Map<String, ErrorStatis> errors) {
		Comparator<java.util.Map.Entry<String, ErrorStatis>> errorCompator = new Comparator<java.util.Map.Entry<String, ErrorStatis>>() {

			@Override
			public int compare(java.util.Map.Entry<String, ErrorStatis> o1, java.util.Map.Entry<String, ErrorStatis> o2) {
				String department1 = o1.getValue().getDepartment();
				String department2 = o2.getValue().getDepartment();
				String productLine1 = o1.getValue().getProductLine();
				String productLine2 = o2.getValue().getProductLine();

				if (department1.equals(department2)) {
					return productLine1.compareTo(productLine2);
				} else {
					return department1.compareTo(department2);
				}
			}
		};
		errors = MapUtils.sortMap(errors, errorCompator);

		for (ErrorStatis temp : errors.values()) {
			Comparator<java.util.Map.Entry<String, ExceptionItem>> compator = new Comparator<java.util.Map.Entry<String, ExceptionItem>>() {

				@Override
				public int compare(java.util.Map.Entry<String, ExceptionItem> o1,
				      java.util.Map.Entry<String, ExceptionItem> o2) {
					return o2.getValue().getCount() - o1.getValue().getCount();
				}
			};
			Map<String, ExceptionItem> bugs = temp.getBugs();
			Map<String, ExceptionItem> exceptions = temp.getExceptions();

			temp.setBugs(MapUtils.sortMap(bugs, compator));
			temp.setExceptions(MapUtils.sortMap(exceptions, compator));
		}
		return errors;
	}

	public class BugReportVisitor extends BaseVisitor {

		private Domain m_currentDomain;

		private Map<String, ErrorStatis> m_errors = new HashMap<String, ErrorStatis>();

		public ErrorStatis findOrCreateErrorStatis(String productLine) {
			ErrorStatis statis = m_errors.get(productLine);

			if (statis == null) {
				statis = new ErrorStatis();
				m_errors.put(productLine, statis);
			}
			return statis;
		}

		public Map<String, ErrorStatis> getErrors() {
			return m_errors;
		}

		@Override
		public void visitDomain(Domain domain) {
			m_currentDomain = domain;
			super.visitDomain(domain);
		}

		@Override
		public void visitExceptionItem(ExceptionItem exceptionItem) {
			String exception = exceptionItem.getId();
			int count = exceptionItem.getCount();
			Project project = findProjectByDomain(m_currentDomain.getId());

			if (project != null) {
				String productLine = project.getProjectLine();
				String department = project.getDepartment();
				ErrorStatis statis = findOrCreateErrorStatis(productLine);

				statis.setDepartment(department);
				statis.setProductLine(productLine);
				m_currentDomain.setDepartment(department);
				m_currentDomain.setProductLine(productLine);

				Map<String, ExceptionItem> items = null;

				if (isBug(m_currentDomain.getId(), exception)) {
					items = statis.getBugs();
				} else {
					items = statis.getExceptions();
				}

				ExceptionItem item = items.get(exception);

				if (item == null) {
					item = new ExceptionItem(exception);
					item.setCount(count);
					item.getMessages().addAll(exceptionItem.getMessages());
					items.put(exception, item);
				} else {
					List<String> messages = item.getMessages();
					item.setCount(item.getCount() + count);
					messages.addAll(exceptionItem.getMessages());

					if (messages.size() > 10) {
						messages = messages.subList(0, 10);
					}
				}
			}
		}
	}

	public class ClearBugReport extends BaseVisitor {

		@Override
		public void visitDomain(Domain domain) {
			String domainName = domain.getId();
			Set<String> removes = new HashSet<String>();
			Map<String, ExceptionItem> items = domain.getExceptionItems();

			for (ExceptionItem item : items.values()) {
				if (!isBug(domainName, item.getId())) {
					removes.add(item.getId());
				}
			}
			for (String remove : removes) {
				items.remove(remove);
			}
		}
	}
}
